package com.joyo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.joyo.backend.model.JvmMetrics;
import com.sun.management.OperatingSystemMXBean;
import org.junit.jupiter.api.Test;

/**
 * JVM 资源采样单元测试（spec-14 第 3 节）：heap 计算与 cpuLoad 基线语义。
 */
class JvmMetricsServiceTest {

    @Test
    void cpuLoad首次采样为负时输出null() {
        OperatingSystemMXBean os = mock(OperatingSystemMXBean.class);
        when(os.getProcessCpuLoad()).thenReturn(-1.0);
        JvmMetrics metrics = new JvmMetricsService(os).sample();

        assertNull(metrics.cpuLoad());
        assertNotNull(metrics.heapMaxMb());
        assertTrue(metrics.heapMaxMb() > 0);
        assertTrue(metrics.heapUsedMb() >= 0);
    }

    @Test
    void cpuLoad保留两位小数() {
        OperatingSystemMXBean os = mock(OperatingSystemMXBean.class);
        when(os.getProcessCpuLoad()).thenReturn(0.0824);
        JvmMetrics metrics = new JvmMetricsService(os).sample();

        assertEquals(0.08, metrics.cpuLoad());
    }

    @Test
    void heapUsed不大于heapMax() {
        JvmMetrics metrics = new JvmMetricsService().sample();

        assertNotNull(metrics.heapMaxMb());
        assertTrue(metrics.heapUsedMb() <= metrics.heapMaxMb());
    }
}
