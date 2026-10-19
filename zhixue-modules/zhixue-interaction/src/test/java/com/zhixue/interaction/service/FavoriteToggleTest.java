package com.zhixue.interaction.service;

import com.zhixue.common.redis.service.RedisService;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.interaction.domain.entity.CourseFavorite;
import com.zhixue.interaction.mapper.CourseFavoriteMapper;
import com.zhixue.interaction.mapper.CourseStatsMapper;
import com.zhixue.interaction.service.impl.FavoriteServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收藏切换必须真正改写数据库。
 *
 * <p>修复前（经网关实测）：deleted 是 @TableLogic 字段，MyBatis-Plus 的 updateById 既不会 SET 它，
 * 又会附加 WHERE deleted=0。于是「取消收藏」从未落库（行仍是 deleted=0），「恢复收藏」匹配不到任何行；
 * 而收藏数却每次都 ±1——课程 2001 只有 1 条收藏，计数漂到了 13。</p>
 */
class FavoriteToggleTest {

    private final CourseFavoriteMapper favoriteMapper = mock(CourseFavoriteMapper.class);
    private final CourseStatsMapper statsMapper = mock(CourseStatsMapper.class);
    private final FavoriteServiceImpl service =
            new FavoriteServiceImpl(favoriteMapper, statsMapper, mock(RedisService.class));

    @BeforeEach
    void loginAsStudent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE);
        request.addHeader(InternalAccessConstants.USER_ID_HEADER, "1003");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(statsMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static CourseFavorite row(int deleted) {
        CourseFavorite f = new CourseFavorite();
        f.setId(82001L);
        f.setUserId(1003L);
        f.setCourseId(2001L);
        f.setDeleted(deleted);
        return f;
    }

    @Test
    void cancelWritesDeletedFlagExplicitly() {
        when(favoriteMapper.selectByUserAndCourseIncludeDeleted(1003L, 2001L)).thenReturn(row(0));
        when(favoriteMapper.updateDeletedFlag(82001L, 0, 1)).thenReturn(1);

        assertThat(service.toggleFavorite(2001L)).isFalse();

        verify(favoriteMapper).updateDeletedFlag(82001L, 0, 1);
        verify(favoriteMapper, never()).updateById(any(CourseFavorite.class));
        verify(statsMapper).incrementFavoriteCount(2001L, -1);
    }

    @Test
    void restoreWritesDeletedFlagExplicitly() {
        when(favoriteMapper.selectByUserAndCourseIncludeDeleted(1003L, 2001L)).thenReturn(row(1));
        when(favoriteMapper.updateDeletedFlag(82001L, 1, 0)).thenReturn(1);

        assertThat(service.toggleFavorite(2001L)).isTrue();

        verify(favoriteMapper, never()).updateById(any(CourseFavorite.class));
        verify(statsMapper).incrementFavoriteCount(2001L, 1);
    }

    @Test
    void countIsUntouchedWhenRowStateDidNotChange() {
        // 并发重复点击：另一请求已改过状态，条件更新影响 0 行，计数不能再变
        when(favoriteMapper.selectByUserAndCourseIncludeDeleted(1003L, 2001L)).thenReturn(row(0));
        when(favoriteMapper.updateDeletedFlag(82001L, 0, 1)).thenReturn(0);

        service.toggleFavorite(2001L);

        verify(statsMapper, never()).incrementFavoriteCount(eq(2001L), anyInt());
    }
}
