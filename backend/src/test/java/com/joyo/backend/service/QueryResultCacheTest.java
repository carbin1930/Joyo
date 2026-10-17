package com.joyo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryResultCacheTest {

    private List<String[]> rows(int count) {
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new String[] {String.valueOf(i)});
        }
        return rows;
    }

    @Test
    void 分页切片() {
        QueryResultCache cache = new QueryResultCache();
        String queryId = cache.put(rows(250), true, 250);

        var page1 = cache.page(queryId, 1, 100);
        var page3 = cache.page(queryId, 3, 100);
        var page4 = cache.page(queryId, 4, 100);

        assertThat(page1.rows()).hasSize(100);
        assertThat(page1.rows().get(0)[0]).isEqualTo("1");
        assertThat(page3.rows().get(0)[0]).isEqualTo("201");
        assertThat(page3.rows()).hasSize(50);
        assertThat(page4.rows()).isEmpty();
        assertThat(page4.totalRows()).isEqualTo(250);
    }

    @Test
    void 分页参数纠正() {
        QueryResultCache cache = new QueryResultCache();
        String queryId = cache.put(rows(1), true, 1);

        var page = cache.page(queryId, 0, 5000);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(1000);
    }

    @Test
    void 过期结果() {
        QueryResultCache cache = new QueryResultCache();
        assertThatThrownBy(() -> cache.page("not-exist", 1, 100))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(e.getMessage()).contains("结果已过期");
                });
    }

    @Test
    void lru淘汰() {
        QueryResultCache cache = new QueryResultCache();
        String oldest = cache.put(rows(1), true, 1);
        for (int i = 0; i < 50; i++) {
            cache.put(rows(1), true, 1);
        }
        assertThatThrownBy(() -> cache.page(oldest, 1, 100))
                .isInstanceOf(BizException.class);
    }
}
