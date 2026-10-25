package com.joyo.backend.service;

import com.joyo.backend.model.JvmMetrics;
import java.lang.management.ManagementFactory;
import org.springframework.stereotype.Component;

/**
 * JVM 资源采样（spec-14 第 3 节）：
 * - 堆内存取 Runtime（used = total - free，max），保留 1 位小数
 * - 进程 CPU 取 OperatingSystemMXBean.getProcessCpuLoad()：该值是
 *   "自上次调用以来的平均负载"，bean 内部维护上次采样基线；
 *   首次调用（无基线）返回负数，输出 null（前端显示"--"）
 */
@Component
public class JvmMetricsService {

    private final com.sun.management.OperatingSystemMXBean os;

    public JvmMetricsService() {
        this((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean());
    }

    /** 包内可见：单测注入 mock bean */
    JvmMetricsService(com.sun.management.OperatingSystemMXBean os) {
        this.os = os;
    }

    public JvmMetrics sample() {
        Runtime rt = Runtime.getRuntime();
        double load = os.getProcessCpuLoad();
        Double cpuLoad = load < 0 ? null : round(load, 2);
        double heapUsedMb = round((rt.totalMemory() - rt.freeMemory()) / 1_048_576.0, 1);
        double heapMaxMb = round(rt.maxMemory() / 1_048_576.0, 1);
        return new JvmMetrics(heapUsedMb, heapMaxMb, cpuLoad);
    }

    private static double round(double value, int digits) {
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
    }
}
