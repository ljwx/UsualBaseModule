package com.jdcr.basedefine.vmm.model

interface IBaseDataRepository<Server> {

    fun createServer(): Server

}