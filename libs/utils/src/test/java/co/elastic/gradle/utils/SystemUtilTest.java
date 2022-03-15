package co.elastic.gradle.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemUtilTest {

    private SystemUtil systemUtil;

    @BeforeEach
    void setUp() {
        systemUtil = new SystemUtil();
    }

    @Test
    void getUsername() {
        assertNotNull(systemUtil.getUsername());
    }

    @Test
    void getUid() {
        assertNotNull(systemUtil.getUid());
    }

    @Test
    void getGid() {
        assertNotNull(systemUtil.getGid());
    }
}