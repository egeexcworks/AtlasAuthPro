import sqlite3
import os
import pathlib

path = os.path.abspath(os.path.join('..', 'TestServer', 'plugins', 'AtlasAuthPro', 'auth.db'))
print('PATH', path)
print('EXISTS', os.path.exists(path))
conn = sqlite3.connect(path)
cur = conn.cursor()
cur.execute('SELECT uuid, username, password_hash, ip_address FROM auth_users')
rows = cur.fetchall()
print('ROWS', len(rows))
for row in rows:
    print(row)
conn.close()
