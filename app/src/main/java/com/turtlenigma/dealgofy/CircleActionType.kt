package com.turtlenigma.dealgofy

enum class CircleActionType {
    PRODUCTIVE_APP,  // launch a configured app and close the intercept
    FOCUS_MODE,      // open duration picker, then DND + brightness + alarm (step 4)
    LOCK_SCREEN      // lock the device immediately
}
