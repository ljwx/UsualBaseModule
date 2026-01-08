package com.jdcr.baseble.core.exception

class PermissionDineException(permission: String?) : Exception("没有系统权限:$permission")