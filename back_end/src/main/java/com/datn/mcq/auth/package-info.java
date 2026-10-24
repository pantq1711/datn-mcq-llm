/**
 * Đăng nhập (W1-1): login, refresh token xoay vòng, logout, đổi mật khẩu, tài khoản admin đầu tiên.
 *
 * <p>Bảng sở hữu: refresh_token.
 * <br>Được phép phụ thuộc: user (qua UserService), common, config.
 *
 * <p>Access token là JWT HS256 (ký bằng JwtEncoder của config.SecurityConfig). Refresh token là chuỗi ngẫu nhiên 256 bit
 * nằm trong cookie HttpOnly; DB chỉ lưu SHA-256. Mỗi lần refresh cấp token mới và thu hồi token cũ; dùng lại token đã
 * thu hồi thì thu hồi cả chuỗi (family) của lần đăng nhập đó.
 */
package com.datn.mcq.auth;
