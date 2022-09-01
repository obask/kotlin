/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

#pragma once

#include <cstdint>
#include <pthread.h>
#include "Common.h"

#define GCLog(epoch, format, ...) RuntimeLogInfo({kTagGC}, "Epoch #%" PRIu64 ": " format, epoch, ##__VA_ARGS__)

namespace kotlin::gc {
class GCHandle {
    uint64_t epoch;
    explicit GCHandle(uint64_t epoch) : epoch(epoch) {}
public:
    static GCHandle create(uint64_t epoch);
    static GCHandle getByEpoch(uint64_t epoch);

    NO_EXTERNAL_CALLS_CHECK void finish();
    NO_EXTERNAL_CALLS_CHECK void finalizersDone();
    NO_EXTERNAL_CALLS_CHECK void finalizersScheduled(uint64_t finalizersCount);
    NO_EXTERNAL_CALLS_CHECK void suspensionRequested();
    NO_EXTERNAL_CALLS_CHECK void threadsAreSuspended();
    NO_EXTERNAL_CALLS_CHECK void threadsAreResumed();
    NO_EXTERNAL_CALLS_CHECK void threadRootSet(int thread_id, uint64_t threadLocalReferences, uint64_t stackReferences);
    NO_EXTERNAL_CALLS_CHECK void globalRootSet(uint64_t globalReferences, uint64_t stableReferences);
    NO_EXTERNAL_CALLS_CHECK void heapUsageBefore(uint64_t objectsCount, uint64_t totalObjectSize);
    NO_EXTERNAL_CALLS_CHECK void heapUsageAfter(uint64_t objectsCount, uint64_t totalObjectSize);
    NO_EXTERNAL_CALLS_CHECK void extraObjectsUsageBefore(uint64_t objectsCount, uint64_t totalObjectSize);
    NO_EXTERNAL_CALLS_CHECK void extraObjectsUsageAfter(uint64_t objectsCount, uint64_t totalObjectSize);
};
}