/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

#include "GCStatistics.hpp"
#include "Mutex.hpp"
#include "Porting.h"

#include "Types.h"
#include "Logging.hpp"
#include <optional>
#include <cinttypes>


extern "C" {
void Kotlin_Internal_GC_GCInfoBuilder_setEpoch(KRef thiz, KLong value);
void Kotlin_Internal_GC_GCInfoBuilder_setStartTime(KRef thiz, KLong value);
void Kotlin_Internal_GC_GCInfoBuilder_setEndTime(KRef thiz, KLong value);
void Kotlin_Internal_GC_GCInfoBuilder_setPauseStartTime(KRef thiz, KLong value);
void Kotlin_Internal_GC_GCInfoBuilder_setPauseEndTime(KRef thiz, KLong value);
void Kotlin_Internal_GC_GCInfoBuilder_setFinalizersDoneTime(KRef thiz, KLong value);
void Kotlin_Internal_GC_GCInfoBuilder_setRootSet(KRef thiz,
                                                 KLong threadLocalReferences, KLong stackReferences,
                                                 KLong globalReferences, KLong stableReferences);
void Kotlin_Internal_GC_GCInfoBuilder_setMemoryUsageBefore(KRef thiz, KNativePtr name, KLong objectsCount, KLong totalObjectsSize);
void Kotlin_Internal_GC_GCInfoBuilder_setMemoryUsageAfter(KRef thiz, KNativePtr name, KLong objectsCount, KLong totalObjectsSize);
}

namespace {

struct MemoryUsage {
    KLong objectsCount;
    KLong totalObjectsSize;
};

struct MemoryUsageMap {
    std::optional<MemoryUsage> heap;
    std::optional<MemoryUsage> meta;

    void build(KRef builder, void (*add)(KRef, KNativePtr, KLong, KLong)) {
        if (heap) {
            add(builder, const_cast<KNativePtr>(static_cast<const void*>("heap")), heap->objectsCount, heap->totalObjectsSize);
        }
        if (meta) {
            add(builder, const_cast<KNativePtr>(static_cast<const void*>("meta")), meta->objectsCount, meta->totalObjectsSize);
        }
    }
};

struct RootSetStatistics {
    KLong threadLocalReferences;
    KLong stackReferences;
    KLong globalReferences;
    KLong stableReferences;
    KLong total() const { return threadLocalReferences + stableReferences + globalReferences + stableReferences; }
};

struct GCInfo {
    std::optional<uint64_t> epoch;
    std::optional<KLong> startTime; // time since process start
    std::optional<KLong> endTime;
    std::optional<KLong> pauseStartTime;
    std::optional<KLong> pauseEndTime;
    std::optional<KLong> finalizersDoneTime;
    std::optional<RootSetStatistics> rootSet;
    MemoryUsageMap memoryUsageBefore;
    MemoryUsageMap memoryUsageAfter;

    void build(KRef builder) {
        if (!epoch) return;
        Kotlin_Internal_GC_GCInfoBuilder_setEpoch(builder, static_cast<KLong>(*epoch));
        if (startTime) Kotlin_Internal_GC_GCInfoBuilder_setStartTime(builder, *startTime);
        if (endTime) Kotlin_Internal_GC_GCInfoBuilder_setEndTime(builder, *endTime);
        if (pauseStartTime) Kotlin_Internal_GC_GCInfoBuilder_setPauseStartTime(builder, *pauseStartTime);
        if (pauseEndTime) Kotlin_Internal_GC_GCInfoBuilder_setPauseEndTime(builder, *pauseEndTime);
        if (finalizersDoneTime) Kotlin_Internal_GC_GCInfoBuilder_setFinalizersDoneTime(builder, *finalizersDoneTime);
        if (rootSet)
            Kotlin_Internal_GC_GCInfoBuilder_setRootSet(
                    builder, rootSet->threadLocalReferences, rootSet->stackReferences, rootSet->globalReferences,
                    rootSet->stableReferences);
        memoryUsageBefore.build(builder, Kotlin_Internal_GC_GCInfoBuilder_setMemoryUsageBefore);
        memoryUsageAfter.build(builder, Kotlin_Internal_GC_GCInfoBuilder_setMemoryUsageAfter);
    }
};

GCInfo last;
GCInfo current;
// This lock can be got by thread in runnable state making parallel mark, so
kotlin::SpinLock<kotlin::MutexThreadStateHandling::kIgnore> lock;

GCInfo* statByEpoch(uint64_t epoch) {
    if (current.epoch == epoch) return &current;
    if (last.epoch == epoch) return &last;
    return nullptr;
}

} // namespace

extern "C" void Kotlin_Internal_GC_GCInfoBuilder_Fill(KRef builder, int id) {
    GCInfo copy;
    {
        kotlin::ThreadStateGuard stateGuard(kotlin::ThreadState::kNative);
        std::lock_guard guard(lock);
        if (id == 0) {
            copy = last;
        } else if (id == 1) {
            copy = current;
        } else {
            return;
        }
    }
    copy.build(builder);
}

namespace kotlin::gc {
GCHandle GCHandle::create(uint64_t epoch) {
    std::lock_guard guard(lock);
    current.epoch = static_cast<KLong>(epoch);
    current.startTime = static_cast<KLong>(konan::getTimeNanos());
    if (last.endTime) {
        GCLog(epoch, "Started. Time since last GC %" PRIu64 " microseconds.", *current.startTime - *last.endTime);
    } else {
        GCLog(epoch, "Started.");
    }
    return getByEpoch(epoch);
}
GCHandle GCHandle::getByEpoch(uint64_t epoch) {
    return GCHandle{epoch};
}
void GCHandle::finish() {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        stat->endTime = static_cast<KLong>(konan::getTimeNanos());
        if (stat->startTime) {
            auto time = (*current.endTime - *current.startTime) / 1000;
            GCLog(epoch, "Finished. Total GC epoch time is %" PRId64" microseconds.", time);
        }

        if (stat == &current) {
            last = current;
            current = {};
        }
    }
}
void GCHandle::suspensionRequested() {
    std::lock_guard guard(lock);
    GCLog(epoch, "Requested thread suspension by thread %d", konan::currentThreadId());
    if (auto* stat = statByEpoch(epoch)) {
        stat->pauseStartTime = static_cast<KLong>(konan::getTimeNanos());
    }
}
void GCHandle::threadsAreSuspended() {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        if (stat->pauseStartTime) {
            auto time = (konan::getTimeNanos() - *stat->pauseStartTime) / 1000;
            GCLog(epoch, "Suspended all threads in %" PRIu64 " microseconds", time);
            return;
        }
    }
}
void GCHandle::threadsAreResumed() {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        stat->pauseEndTime = static_cast<KLong>(konan::getTimeNanos());
        if (stat->pauseStartTime) {
            auto time = (*stat->pauseEndTime - *stat->pauseStartTime) / 1000;
            GCLog(epoch, "Resume all threads. Total pause time is %" PRId64 " microseconds.", time);
            return;
        }
    }
}
void GCHandle::finalizersDone() {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        stat->finalizersDoneTime = static_cast<KLong>(konan::getTimeNanos());
        if (stat->endTime) {
            auto time = (*stat->finalizersDoneTime - *stat->endTime) / 1000;
            GCLog(epoch, "Finalization is done in %" PRId64 " microseconds after epoch end.", time);
            return;
        }
    }
    GCLog(epoch, "Finalization is done.");
}
void GCHandle::finalizersScheduled(uint64_t finalizersCount) {
    GCLog(epoch, "Finalization is scheduled for %" PRIu64 " objects.", finalizersCount);
}
void GCHandle::threadRootSet(int thread_id, uint64_t threadLocalReferences, uint64_t stackReferences) {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        if (!stat->rootSet) {
            stat->rootSet = RootSetStatistics{0, 0, 0, 0};
        }
        stat->rootSet->stackReferences += static_cast<KLong>(stackReferences);
        stat->rootSet->threadLocalReferences += static_cast<KLong>(threadLocalReferences);
        GCLog(epoch, "Collected root set for thread #%d: stack=%" PRIu64 " tls=%" PRIu64 ". Total root set size is %" PRId64 "\n",
              thread_id, stackReferences, threadLocalReferences, stat->rootSet->total());
    }
}
void GCHandle::globalRootSet(uint64_t globalReferences, uint64_t stableReferences) {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        if (!stat->rootSet) {
            stat->rootSet = RootSetStatistics{0, 0, 0, 0};
        }
        stat->rootSet->globalReferences += static_cast<KLong>(globalReferences);
        stat->rootSet->stableReferences += static_cast<KLong>(stableReferences);

        GCLog(epoch, "Collected global root set global=%" PRIu64 " stableRef=%" PRIu64 ". Total root set size is %" PRId64,
              globalReferences, stableReferences, stat->rootSet->total());
    }
}


void GCHandle::heapUsageBefore(uint64_t objectsCount, uint64_t totalObjectsSize) {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        stat->memoryUsageBefore.heap = MemoryUsage{static_cast<KLong>(objectsCount), static_cast<KLong>(totalObjectsSize)};
    }
}
void GCHandle::heapUsageAfter(uint64_t objectsCount, uint64_t totalObjectsSize) {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        stat->memoryUsageAfter.heap = MemoryUsage{static_cast<KLong>(objectsCount), static_cast<KLong>(totalObjectsSize)};
        if (stat->memoryUsageBefore.heap) {
            GCLog(epoch, "Collected %" PRId64 " heap objects of total size %" PRId64 ".",
                  stat->memoryUsageBefore.heap->objectsCount - stat->memoryUsageAfter.heap->objectsCount,
                  stat->memoryUsageBefore.heap->totalObjectsSize - stat->memoryUsageAfter.heap->totalObjectsSize);
        }
        GCLog(epoch, "%" PRId64 " heap objects of total size %" PRId64 " are still alive.",
              stat->memoryUsageAfter.heap->objectsCount, stat->memoryUsageAfter.heap->totalObjectsSize);
    }
}

void GCHandle::extraObjectsUsageBefore(uint64_t objectsCount, uint64_t totalObjectsSize) {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        stat->memoryUsageBefore.meta = MemoryUsage{static_cast<KLong>(objectsCount), static_cast<KLong>(totalObjectsSize)};
    }
}
void GCHandle::extraObjectsUsageAfter(uint64_t objectsCount, uint64_t totalObjectsSize) {
    std::lock_guard guard(lock);
    if (auto* stat = statByEpoch(epoch)) {
        stat->memoryUsageAfter.meta = MemoryUsage{static_cast<KLong>(objectsCount), static_cast<KLong>(totalObjectsSize)};
        if (stat->memoryUsageBefore.meta) {
            GCLog(epoch, "Collected %" PRId64 " meta objects of total size %" PRId64 ".",
                  stat->memoryUsageBefore.meta->objectsCount - stat->memoryUsageAfter.meta->objectsCount,
                  stat->memoryUsageBefore.meta->totalObjectsSize - stat->memoryUsageAfter.meta->totalObjectsSize);
        }
        GCLog(epoch, "%" PRId64 " meta objects of total size %" PRId64 " are still alive.",
              stat->memoryUsageAfter.meta->objectsCount, stat->memoryUsageAfter.meta->totalObjectsSize);
    }
}
} // namespace kotlin::gc