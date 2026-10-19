package org.example.cipherws.note.user.mapper;

import org.apache.ibatis.annotations.Param;
import org.example.cipherws.note.common.entity.NoteUser;

/**
 * 用户表的数据访问接口。SQL 全部在 resources/mapper/UserMapper.xml 里。
 *
 * 为什么 SQL 放 XML 而不是写在注解里：动态条件（比如可选的关键词、
 * 批量 IN）在 XML 里用 {@code <if>} / {@code <foreach>} 写起来清楚得多，
 * 而且改 SQL 不用重新编译。
 */
public interface UserMapper {

    Long countByUsername(@Param("username") String username);

    Long countByEmail(@Param("email") String email);

    /** 登录用：要拿到 password 摘要才能比对，所以这个方法**会**带出密码字段 */
    NoteUser findByUsername(@Param("username") String username);

    /** 查资料用 */
    NoteUser findById(@Param("id") long id);

    /** 改密码前校验旧密码：只取摘要，不把整行捞出来 */
    String findPasswordById(@Param("id") long id);

    /** 插入后会把自增主键写回实体的 id（XML 里配了 useGeneratedKeys） */
    int insert(NoteUser user);

    int updateProfile(@Param("id") long id,
                      @Param("nickname") String nickname,
                      @Param("bio") String bio,
                      @Param("avatar") String avatar);

    /** 换绑邮箱。调用前 Service 已确认验证码通过且该邮箱未被别人占用 */
    int updateEmail(@Param("id") long id, @Param("email") String email);

    /** 只更新头像，不动昵称/简介 —— 上传头像时不该顺带覆盖别的字段 */
    int updateAvatar(@Param("id") long id, @Param("avatar") String avatar);

    /** 换绑前查重：这个邮箱是否已经被**别的**账号占用 */
    Long countByEmailExcluding(@Param("userId") long userId, @Param("email") String email);

    int updatePassword(@Param("id") long id, @Param("password") String password);

    /** 找回密码走邮箱，因为此时用户还没登录、拿不到 id */
    int updatePasswordByEmail(@Param("email") String email, @Param("password") String password);

    int insertUserLog(@Param("userId") long userId,
                      @Param("type") String type,
                      @Param("detail") String detail,
                      @Param("ip") String ip);
}
