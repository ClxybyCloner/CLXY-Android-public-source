# CLXY | Cloner Xray

CLXY 是非官方 Windows VPN 客户端。连接与登录逻辑保持不变；使用时需要 Firefox Account 和外部 VPN 代理服务。登录页面由账户服务提供方托管，因此可能显示其自身品牌。

## Windows 构建

在 Windows 10/11 x64 安装 Flutter stable、`core/go.mod` 指定的 Go、PowerShell 7 和 Inno Setup 6，然后在项目根目录运行：

```powershell
pwsh -File scripts/build-windows.ps1
```

程序文件夹、便携 ZIP 和当前用户安装程序会生成在 `dist/`，文件名以 `CLXY` 开头。本 Linux 环境尚未编译或运行 Windows 文件，因此这还不是经过 Windows 测试的版本。

## 许可

原始 MIT 版权与许可声明保留在 [`LICENSE`](../../LICENSE) 中；第三方依赖声明也予以保留。
