package com.joyo.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.LogTailResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 日志尾部读取单元测试（spec-13 第 3/5 节）：tail 契约、文件不存在空态、越界 1001。
 */
class LogControllerTest {

    @TempDir
    Path tmpDir;

    private LogController controllerFor(String fileName) throws IOException {
        return new LogController(tmpDir.resolve(fileName).toString());
    }

    @Test
    void 默认返回尾部200行() throws IOException {
        Path log = tmpDir.resolve("app.log");
        Files.writeString(log, lines(500), StandardCharsets.UTF_8);
        ApiResponse<LogTailResponse> resp = controllerFor("app.log").tail(LogController.DEFAULT_TAIL);
        LogTailResponse data = resp.data();

        long expectedSize = IntStream.range(0, 500).mapToLong(i -> ("line" + i).length() + 1L).sum();
        assertEquals(expectedSize, data.sizeBytes());
        assertEquals(200, data.lines().size());
        assertEquals("line499", data.lines().get(data.lines().size() - 1));
        assertEquals("line300", data.lines().get(0));
        assertTrue(data.filePath().endsWith("app.log"));
    }

    @Test
    void 行数不足时返回全部() throws IOException {
        Path log = tmpDir.resolve("app.log");
        Files.writeString(log, "a\nb\nc", StandardCharsets.UTF_8);
        LogTailResponse data = controllerFor("app.log").tail(100).data();

        assertEquals(3, data.lines().size());
        assertEquals(5, data.sizeBytes());
    }

    @Test
    void 文件不存在返回空列表() throws IOException {
        LogTailResponse data = controllerFor("missing.log").tail(200).data();

        assertEquals(0, data.lines().size());
        assertEquals(0, data.sizeBytes());
    }

    @Test
    void tail越界返回1001() throws IOException {
        LogController controller = controllerFor("app.log");
        BizException low = assertThrows(BizException.class, () -> controller.tail(0));
        assertEquals(ErrorCode.PARAM_INVALID, low.getErrorCode());
        BizException high = assertThrows(BizException.class, () -> controller.tail(2001));
        assertEquals(ErrorCode.PARAM_INVALID, high.getErrorCode());
    }

    @Test
    void 文件以换行结尾时不产生空尾行() throws IOException {
        Path log = tmpDir.resolve("app.log");
        Files.writeString(log, "a\nb\n", StandardCharsets.UTF_8);
        LogTailResponse data = controllerFor("app.log").tail(10).data();

        assertEquals(2, data.lines().size());
    }

    @Test
    void CRLF行尾正常拆分() throws IOException {
        Path log = tmpDir.resolve("app.log");
        Files.writeString(log, "a\r\nb\r\n", StandardCharsets.UTF_8);
        LogTailResponse data = controllerFor("app.log").tail(10).data();

        assertEquals(2, data.lines().size());
        assertEquals("b", data.lines().get(1));
    }

    private static String lines(int count) {
        return IntStream.range(0, count).mapToObj(i -> "line" + i)
                .collect(Collectors.joining("\n", "", "\n"));
    }
}
