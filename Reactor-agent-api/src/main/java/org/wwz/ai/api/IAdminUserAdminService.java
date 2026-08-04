package org.wwz.ai.api;

import org.wwz.ai.api.dto.AdminUserLoginRequestDTO;
import org.wwz.ai.api.dto.AdminUserQueryRequestDTO;
import org.wwz.ai.api.dto.AdminUserRequestDTO;
import org.wwz.ai.api.dto.AdminUserResponseDTO;
import org.wwz.ai.api.response.Response;

import java.util.List;


/**
 * 管理员用户配置的应用服务契约。
 *
 * <p>接口同时提供主键、业务用户 ID 和用户名三类查询入口；调用方应根据已有标识选择对应方法，
 * 不把业务 ID 当作数据库主键使用。</p>
 */
public interface IAdminUserAdminService {

    /**
     * 创建管理员用户
     * @param request 管理员用户请求对象
     * @return 操作结果
     */
    Response<Boolean> createAdminUser(AdminUserRequestDTO request);

    /**
     * 根据ID更新管理员用户
     * @param request 管理员用户请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAdminUserById(AdminUserRequestDTO request);

    /**
     * 根据用户ID更新管理员用户
     * @param request 管理员用户请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAdminUserByUserId(AdminUserRequestDTO request);

    /**
     * 根据ID删除管理员用户
     * @param id 主键ID
     * @return 操作结果
     */
    Response<Boolean> deleteAdminUserById(Long id);

    /**
     * 根据用户ID删除管理员用户
     * @param userId 用户ID
     * @return 操作结果
     */
    Response<Boolean> deleteAdminUserByUserId(String userId);

    /**
     * 根据ID查询管理员用户
     * @param id 主键ID
     * @return 管理员用户对象
     */
    Response<AdminUserResponseDTO> queryAdminUserById(Long id);

    /**
     * 根据用户ID查询管理员用户
     * @param userId 用户ID
     * @return 管理员用户对象
     */
    Response<AdminUserResponseDTO> queryAdminUserByUserId(String userId);

    /**
     * 根据用户名查询管理员用户
     * @param username 用户名
     * @return 管理员用户对象
     */
    Response<AdminUserResponseDTO> queryAdminUserByUsername(String username);

    /**
     * 查询所有启用状态的管理员用户
     * @return 管理员用户列表
     */
    Response<List<AdminUserResponseDTO>> queryEnabledAdminUsers();

    /**
     * 根据状态查询管理员用户列表
     * @param status 状态
     * @return 管理员用户列表
     */
    Response<List<AdminUserResponseDTO>> queryAdminUsersByStatus(Integer status);

    /**
     * 根据条件查询管理员用户列表
     * @param request 查询条件
     * @return 管理员用户列表
     */
    Response<List<AdminUserResponseDTO>> queryAdminUserList(AdminUserQueryRequestDTO request);

    /**
     * 查询所有管理员用户
     * @return 管理员用户列表
     */
    Response<List<AdminUserResponseDTO>> queryAllAdminUsers();

    /**
     * 用户登录验证
     * @param request 登录请求对象
     * @return 管理员用户对象
     */
    Response<AdminUserResponseDTO> loginAdminUser(AdminUserLoginRequestDTO request);

    /**
     * 用户登录校验
     * @param request 登录请求对象
     * @return 登录校验结果，成功返回true，失败返回false
     */
    Response<Boolean> validateAdminUserLogin(AdminUserLoginRequestDTO request);

}
