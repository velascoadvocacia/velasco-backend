package com.example.lawyer

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.annotations.QuarkusMain

@QuarkusMain
class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Quarkus.run(*args)
        }
    }
}
