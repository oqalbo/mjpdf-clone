// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

open class BaselineFailure(message: String) : AssertionError(message)

class BaselinePinMismatch(message: String) : BaselineFailure(message)

class BaselineGateFailure(message: String) : BaselineFailure(message)

class BaselineMissingGolden(message: String) : BaselineFailure(message)

class BaselineImageMismatch(message: String) : BaselineFailure(message)

class BaselineRecorded(message: String) : BaselineFailure(message)
