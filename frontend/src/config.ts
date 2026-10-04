/**
 * 全局环境配置。
 *
 * BASE_URL：后端服务地址。
 * - 直接用电脑的「局域网 IP」最省事：模拟器跑在电脑上，访问电脑自己的局域网 IP 同样通，
 *   真机也能通过同一 WiFi 访问，不必在两种调试方式间来回改。
 * - 换网络后 IP 可能变化，真机连不上时先确认本机 WLAN 地址：
 *   PowerShell: (Get-NetIPAddress -AddressFamily IPv4 | ? { $_.InterfaceAlias -eq 'WLAN' }).IPAddress
 * - ⚠️ 别填 VMware 虚拟网卡地址（常见 192.168.122.x / 192.168.123.x），手机访问不到。
 * - 真机还需放行 Windows 防火墙入站 8080（管理员 PowerShell）：
 *   New-NetFirewallRule -DisplayName "ReadTalk Backend 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow -Profile Private,Public
 * - 内网穿透：填穿透后的公网域名
 */
export const BASE_URL = 'http://192.168.0.103:8080'
