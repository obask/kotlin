@file:Suppress("NO_ACTUAL_FOR_EXPECT")

package kotlin.ranges

expect class PlatformIntRange : PlatformIntProgression, ClosedRange<PlatformInt> {
    override fun isEmpty(): Boolean

    companion object {
        val EMPTY: PlatformIntRange
    }
}
