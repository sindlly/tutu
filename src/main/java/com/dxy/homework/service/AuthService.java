package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.client.WxMiniAppClient;
import com.dxy.homework.common.BizException;
import com.dxy.homework.config.AuthProperties;
import com.dxy.homework.config.WxProperties;
import com.dxy.homework.dto.WxLoginRequest;
import com.dxy.homework.entity.Student;
import com.dxy.homework.entity.User;
import com.dxy.homework.mapper.StudentMapper;
import com.dxy.homework.mapper.UserMapper;
import com.dxy.homework.vo.LoginVo;
import com.dxy.homework.vo.StudentVo;
import com.dxy.homework.vo.UserVo;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String DEFAULT_STUDENT_NAME = "宝贝";
    private static final int DEFAULT_GRADE = 3;

    private final WxMiniAppClient wxMiniAppClient;
    private final WxProperties wxProperties;
    private final AuthProperties authProperties;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TokenService tokenService;

    public AuthService(WxMiniAppClient wxMiniAppClient,
                       WxProperties wxProperties,
                       AuthProperties authProperties,
                       UserMapper userMapper,
                       StudentMapper studentMapper,
                       TokenService tokenService) {
        this.wxMiniAppClient = wxMiniAppClient;
        this.wxProperties = wxProperties;
        this.authProperties = authProperties;
        this.userMapper = userMapper;
        this.studentMapper = studentMapper;
        this.tokenService = tokenService;
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVo login(WxLoginRequest request) {
        String openId = resolveOpenId(request);

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getOpenId, openId));
        boolean newUser = false;
        if (user == null) {
            user = createUser(openId);
            newUser = true;
            log.info("新用户注册 userId={}", user.getId());
        } else {
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);
        }

        Student student = ensureDefaultStudent(user, request);
        String token = tokenService.issue(user.getId());
        return new LoginVo(token, tokenService.ttl().toSeconds(), newUser,
                UserVo.from(user), StudentVo.from(student));
    }

    /**
     * 正常走微信 code2session 换 openid。
     *
     * <p>若请求显式带了 devIdentity 且服务端开了 dev-login-enabled，则跳过微信，
     * 用该标识拼出稳定的假 openid —— 这样才能在本地模拟出多个用户，
     * 从而真正验证越权防护（同一个标识始终是同一个用户，可反复调试）。
     */
    private String resolveOpenId(WxLoginRequest request) {
        if (StringUtils.hasText(request.devIdentity())) {
            if (!wxProperties.devLoginEnabled()) {
                throw BizException.badRequest("开发登录未开启");
            }
            log.warn("使用开发身份登录 identity={}，禁止用于生产环境", request.devIdentity());
            return authProperties.devOpenId() + ":" + sanitizeDevIdentity(request.devIdentity());
        }

        if (!StringUtils.hasText(request.code())) {
            throw BizException.badRequest("缺少微信登录凭证");
        }
        return wxMiniAppClient.code2Session(request.code()).openId();
    }

    private String sanitizeDevIdentity(String code) {
        if (code == null || code.isBlank()) {
            return "default";
        }
        String sanitized = code.replaceAll("[^A-Za-z0-9_-]", "");
        if (sanitized.isBlank()) {
            return "default";
        }
        return sanitized.length() > 24 ? sanitized.substring(0, 24) : sanitized;
    }

    private User createUser(String openId) {
        User user = new User();
        user.setOpenId(openId);
        user.setNickname("家长");
        user.setRole(1);
        user.setQuotaDaily(10);
        user.setStatus(1);
        user.setLastLoginAt(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
        return user;
    }

    /** 保证每个用户至少有一个孩子档案，否则提交作业时会因为缺少 studentId 而卡住。 */
    private Student ensureDefaultStudent(User user, WxLoginRequest request) {
        Student existing = studentMapper.selectOne(Wrappers.<Student>lambdaQuery()
                .eq(Student::getUserId, user.getId())
                .orderByAsc(Student::getSortNo)
                .orderByAsc(Student::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        Student student = new Student();
        student.setUserId(user.getId());
        student.setName(StringUtils.hasText(request.studentName())
                ? request.studentName().trim() : DEFAULT_STUDENT_NAME);
        student.setGrade(request.grade() == null ? DEFAULT_GRADE : request.grade());
        student.setSortNo(0);
        student.setDeleted(0);
        studentMapper.insert(student);
        return student;
    }
}
