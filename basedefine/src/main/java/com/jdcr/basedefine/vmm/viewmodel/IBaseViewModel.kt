package com.jdcr.basedefine.vmm.viewmodel

interface IBaseViewModel<M> {

    fun createRepository(): M

}