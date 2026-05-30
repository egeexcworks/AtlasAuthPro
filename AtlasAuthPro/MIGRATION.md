# 🚀 AtlasAuthPro Multi-Auth Migration Guide (nLogin & OpenLogin)
## For Server Owners - Super Simple Steps

**🚀 AtlasAuthPro Perfect OpeNLogin Migration Guide v2.1** 

**Good news!** Your players keep passwords from **OpeNLogin**! No re-registration!

### ✅ **PERFECT Migration Checklist**

#### **STEP 1: Prepare (3 minutes)**
```
☐ Backup server 
☐ Install AtlasAuthPro.jar (KEEP OpeNLogin.jar)
☐ Start server ONCE (creates tables)
☐ Start with BOTH plugins
```

#### **STEP 2: Detect & Backup (1 minute)**
```
/admin detect
✅ "✓ OpeNLogin found! (X players)"
/admin migrate dryrun
✅ Auto-backup created!
```

#### **STEP 3: Migrate (Safe!)**
```
/admin migrate start
✅ Progress bar, reports saved
✅ Backup: plugins/AtlasAuthPro/migration_backups/
✅ Report: plugins/AtlasAuthPro/migration_reports/
```

#### **STEP 4: Test & Clean**
```
☐ Players login OLD password ✅
☐ Remove OpeNLogin.jar
☐ Restart ✅
```

### 🎯 **Commands**
| Command | Action |
|---------|---------|
| `/admin detect` | 🔍 Find OpeNLogin |
| `/admin migrate dryrun` | 👀 Preview + Backup |
| `/admin migrate start` | 🚀 Migrate |
| `/admin migrate cancel` | ⏹️ Stop |

### ⚙️ **Config (config.yml)**
```yaml
migration:
  backup: true           # Auto-backup DB
  provider: auto        # auto detects OpeNLogin
```

### 🆕 **Features Added**
- ✅ **Auto OpeNLogin detection** (plugins/OpeNLogin/accounts.db)
- ✅ **DB Backup** before migrate
- ✅ **Detailed reports** (TXT files)
- ✅ **Progress bar** (real-time)
- ✅ **Skip duplicates**
- ✅ **BCrypt passthrough** (same passwords)
- ✅ **Error handling** + logs

**OpeNLogin → AtlasAuthPro = PERFECT! 🎉**

**Last updated: AtlasAuthPro 2.1.0**


**Permission:** `atlasauth.admin`

---

## ❓ FAQ

**Q: Supported plugins?**
A: **nLogin** & **OpenLogin** (SQLite/MySQL)

**Q: Passwords work after?**
A: **Yes!** BCrypt compatible.

**Q: Provider "auto" ?**
A: Tries nLogin first, then OpenLogin.

**Q: Multiple migrations?**
A: Yes, skips duplicates.

---

## ⚠️ Safety
```
✅ Backup first
✅ Dryrun first
✅ Keep old plugin until tested
✅ Async - no lag
```

## 🔧 Config.yml
```yaml
migration:
  provider: auto  # auto, nlogin, openlogin
  batch-size: 50
```

**Last updated:** AtlasAuthPro 2.0.3 (OpenLogin support!)

#### **STEP 3: Preview (Safe Test)**
```
/admin migrate dryrun

✅ See what will happen:
"Preview mode - no changes made"
"123 players ready to migrate"
```

#### **STEP 4: Migrate (2-5 minutes)**
```
/admin migrate start

✅ Watch progress:
"Progress: 50/123 (40%)"
"Successfully migrated: 120"
"Skipped: 3 (already registered)"
```

#### **STEP 5: Test & Finish**
```
☐ Players login with OLD nLogin password
☐ Remove nLogin jar
☐ Restart server
☐ Done! 🎉
```

---

## 🎯 Quick Commands Reference
| Command | What it does |
|---------|--------------|
| `/admin detect` | 🔍 Find nLogin data |
| `/admin migrate dryrun` | 👀 Preview (SAFE) |
| `/admin migrate start` | 🚀 Do migration |
| `/admin migrate cancel` | ⏹️ Stop if needed |

**Permission needed:** `atlasauth.admin`

---

## ❓ FAQ - Common Questions

**Q: Do players need new passwords?**
A: **No!** Same nLogin password works perfectly.

**Q: What if nLogin not detected?**
A: Make sure nLogin folder exists in `plugins/` with database file.

**Q: Migration stuck or slow?**
A: Normal for 1000+ players. Wait - it's async (no lag).

**Q: Some players can't login?**
A: Rare password format issue. They can `/resetpassword`.

**Q: Can I run migration multiple times?**
A: Yes! Skips existing players automatically.

---

## ⚠️ Safety First
```
✅ Always backup first
✅ Test dryrun first  
✅ Keep nLogin until verified
✅ Migration skips duplicates
✅ Passwords stay the same (BCrypt)
```

---

## 🔧 Advanced (Config.yml)
```yaml
migration:
  enabled: true           # Enable migration
  auto-detect: true       # Auto find nLogin
  batch-size: 50          # Speed vs lag
```

---

## 🛠 Technical Appendix (Developers)

**How it works:**
- Auto detects SQLite/MySQL
- Maps columns dynamically (uuid/password/ip)
- BCrypt passwords transfer directly
- Async processing, progress reports
- Skips registered players

**Supported nLogin tables:** `nlogin_users`, `players`, `accounts`, `users`

**Database files:** `nlogin.db`, `nlogin.sqlite`, `database.db`

**MigrationManager.java** handles all logic safely.

---

**Questions?** Join our Discord or check console logs.

**Last updated:** AtlasAuthPro 2.0.1**

