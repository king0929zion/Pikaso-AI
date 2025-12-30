package com.example.operit.virtualdisplay.shower

import android.content.Context
import com.ai.assistance.showerclient.ShellCommandResult
import com.ai.assistance.showerclient.ShellIdentity
import com.ai.assistance.showerclient.ShellRunner
import com.example.operit.logging.AppLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class PikasoShowerShellRunner(
    private val context: Context,
) : ShellRunner {
    override suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult {
        return withContext(Dispatchers.IO) {
            if (!Shizuku.pingBinder()) {
                return@withContext ShellCommandResult(
                    success = false,
                    stdout = "",
                    stderr = "Shizuku 未运行，无法执行命令",
                    exitCode = -1,
                )
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return@withContext ShellCommandResult(
                    success = false,
                    stdout = "",
                    stderr = "Shizuku 未授权，无法执行命令",
                    exitCode = -1,
                )
            }

            // Shizuku 仅能以 shell 身份执行；这里将 DEFAULT/ROOT/SHELL 统一映射为 shell。
            AppLog.d("ShowerShell", "run identity=$identity cmd=$command")

            val proc =
                try {
                    newProcess(arrayOf("sh", "-c", command))
                } catch (e: Exception) {
                    return@withContext ShellCommandResult(
                        success = false,
                        stdout = "",
                        stderr = "启动 Shizuku 进程失败：${e.message ?: e.javaClass.simpleName}",
                        exitCode = -1,
                    )
                }

            val finished = waitFor(proc, 30_000L)
            if (!finished) {
                runCatching { proc.destroy() }
                return@withContext ShellCommandResult(
                    success = false,
                    stdout = "",
                    stderr = "命令超时",
                    exitCode = -1,
                )
            }

            val exitCode = runCatching { proc.exitValue() }.getOrNull() ?: -1
            val stdout = runCatching { proc.inputStream.bufferedReader().use { it.readText() } }.getOrNull().orEmpty()
            val stderr = runCatching { proc.errorStream.bufferedReader().use { it.readText() } }.getOrNull().orEmpty()
            ShellCommandResult(
                success = exitCode == 0,
                stdout = stdout.trim(),
                stderr = stderr.trim(),
                exitCode = exitCode,
            )
        }
    }

    private fun waitFor(proc: Process, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        Thread {
            runCatching { proc.waitFor() }
            latch.countDown()
        }.start()
        return latch.await(timeoutMs.coerceAtLeast(100L), TimeUnit.MILLISECONDS)
    }

    private fun escapeShellArg(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun newProcess(cmd: Array<String>): Process {
        val m =
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(null, cmd, null, null) as Process
    }
}
