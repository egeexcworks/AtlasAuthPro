# AtlasAuthPro 🛡️

![Version](https://img.shields.io/badge/Version-2.0.0-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1+-brightgreen)
![Spigot/Paper](https://img.shields.io/badge/Server-Spigot%2FPaper-brightgreen)

Advanced Authentication System for Minecraft servers with a powerful GUI Admin Panel.

---

## 📋 Features

### 🔐 Core Authentication
- **Secure Registration** - `/register <password> <confirm>`
- **Secure Login** - `/login <password>`
- **Session Management** - Stay logged in until logout or disconnect
- **BCrypt Password Hashing** - Industry-standard security
- **Password Strength Validation** - Configurable requirements

### 🔄 **nLogin Migration** - Import player data seamlessly
- Auto-detect nLogin database (SQLite/MySQL)
- Transfer passwords directly (no re-registration!)
- Preview mode + safe migration
- `/admin detect` & `/admin migrate` commands
- See [MIGRATION.md](MIGRATION.md) for full guide

### 🚫 Player Restrictions (Before Login)
- Freeze player movement
- Blindness effect for focus
- Block chat and commands
- Prevent block breaking/placing
- Disable damage and interactions
- Teleport to login area

### 🔒 Advanced Security
- **Two-Factor Authentication (2FA)** - Via authenticator apps
- **IP Tracking** - Log and monitor login IPs
- **Failed Login Protection** - Auto-lock after attempts
- **Session Timeout** - Auto-logout after inactivity
- **Premium Account Verification** - Optional Mojang validation

### 💾 Storage Options
- **SQLite** - Built-in, perfect for small servers
- **MySQL** - For larger networks

### 🎮 In-Game Admin Panel
- **GUI-based** admin panel with `/authadmin`
- **Player Management** - Force login/logout, reset passwords
- **Security Monitoring** - View failed attempts, active sessions
- **Settings Configuration** - Toggle features in-game

---

## 📖 Commands

### Player Commands
| Command | Description | Alias |
|---------|-------------|-------|
| `/register <password> <confirm>` | Register an account | `/reg` |
| `/login <password>` | Login to your account | `/l` |
| `/logout` | Logout from your account | `/lo` |
| `/changepassword <old> <new>` | Change your password | `/cp` |
| `/resetpassword` | Request password reset | `/rp` |
| `/2fa <code>` | Enable or verify 2FA | `/2fa` |

### Admin Commands
| Command | Description | Alias |
|---------|-------------|-------|
| `/authadmin` | Open admin GUI panel | `/aa` |
| `/admin forcereset <player>` | Reset player password | - |
| `/admin forcelogout <player>` | Force player logout | - |
| `/admin view <player>` | View player info | - |
| `/admin list` | List all players | - |
| `/admin stats` | View statistics | - |
| `/admin reload` | Reload configuration | - |

---

## ⚙️ Configuration

### Database Settings
```
yaml
database:
  type: SQLite  # SQLite or MySQL
  host: localhost
  port: 3306
  database: atlasauth
  username: root
  password: ''
  file: auth.db
```

### Security Settings
```
yaml
security:
  min-password-length: 6
  max-password-length: 32
  require-special-chars: false
  require-numbers: false
  bcrypt-work-factor: 12
  max-login-attempts: 3
  lockout-time: 300  # seconds
  session-duration: 86400  # 24 hours
```

### Player Restrictions
```
yaml
security:
  blindness-effect: true
  freeze-player: true
  block-chat: true
  block-commands: true
  block-movement: true
  block-building: true
  block-damage: true
  block-interactions: true
```

---

## 🔧 Installation

1. **Download** the latest `AtlasAuthPro-2.0.0.jar`
2. **Place** it in your server's `plugins/` folder
3. **Restart** or reload your server
4. **Configure** the `config.yml` to your needs

### Database Setup

**For SQLite (Default):**
- No additional setup required
- Database file created automatically

**For MySQL:**
1. Create a database named `atlasauth`
2. Update config.yml with your MySQL credentials
3. Restart the server

---

## 🎨 Admin GUI Guide

### Main Menu
- **Player Management** - View and manage players
- **Security Dashboard** - Monitor failed logins
- **Settings** - Toggle plugin features
- **Statistics** - Server authentication stats

### Player Actions
- **Force Login** - Log player in
- **Force Logout** - Log player out
- **Reset Password** - Generate new password
- **Ban/Unban** - Account security bans

### GUI Navigation
- Click items to select
- Use arrows to navigate pages
- Hover for tooltips

---

## 📝 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `atlasauth.*` | All permissions | OP |
| `atlasauth.admin` | Admin access | OP |
| `atlasauth.login` | Login command | All |
| `atlasauth.register` | Register command | All |
| `atlasauth.logout` | Logout command | All |
| `atlasauth.changepassword` | Change password | All |
| `atlasauth.resetpassword` | Reset password | All |
| `atlasauth.2fa` | 2FA commands | All |

---

## 🔄 Supported Aliases

### Login
`/login`, `/l`, `/log`, `/signin`

### Register
`/register`, `/reg`, `/signup`, `/createaccount`

### Logout
`/logout`, `/lo`, `/signout`

### Admin
`/authadmin`, `/aa`, `/authadminpanel`

---

## 🛠️ Troubleshooting

### "Database connection failed"
- Check your database credentials in config.yml
- For MySQL, ensure the database exists
- Check server console for specific errors

### "Native library error"
- The plugin includes native SQLite libraries
- Should work automatically on Windows, Linux, Mac

### "Player not moving after login"
- Relog or wait 2 seconds
- Check for conflicting plugins

### "2FA not working"
- Ensure server has internet access
- Use Google Authenticator or similar apps

---

## 🔄 Updates

### Version 2.0.0
- ✅ Complete plugin rewrite
- ✅ GUI Admin Panel
- ✅ 2FA Support
- ✅ SQLite native libraries included
- ✅ Session management
- ✅ IP tracking
- ✅ Multi-language support

---

## 📞 Support

For issues and feature requests:
- Check the configuration
- Review server console logs
- Ensure compatible Spigot/Paper version

---

## ❤️ Credits

**Developer:** AtlasAuth 

**Libraries Used:**
- BCrypt (password hashing)
- SQLite JDBC (database)
- PaperMC API (server integration)

---

## 📄 License

This plugin is provided as-is for server authentication purposes.

---

**Thanks for using AtlasAuthPro!** 🎉

If you have any questions or need help, don't hesitate to reach out. Happy authenticating! 🛡️
