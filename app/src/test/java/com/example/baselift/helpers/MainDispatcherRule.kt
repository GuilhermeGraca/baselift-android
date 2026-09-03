package com.example.baselift.helpers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Regra JUnit que substitui o Dispatchers.Main pelo TestDispatcher.
 *
 * Necessária porque o Dispatchers.Main só existe em Android (usa o Looper da UI thread).
 * Nos testes unitários (JVM), não há Looper, por isso o viewModelScope.launch crasharia
 * com "Module with the Main dispatcher had failed to initialize".
 *
 * Uso: adicionar como @get:Rule no ficheiro de teste.
 *
 * @param testDispatcher o dispatcher a usar nos testes (UnconfinedTestDispatcher por defeito
 *        para que as coroutines corram imediatamente sem necessidade de advanceUntilIdle())
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
