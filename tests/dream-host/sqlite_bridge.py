import sys,json,sqlite3
c=sqlite3.connect(':memory:',isolation_level=None);c.row_factory=sqlite3.Row;c.execute('PRAGMA foreign_keys=ON')
for line in sys.stdin:
 try:
  q=json.loads(line);cur=c.execute(q['sql'],q['args']);r={'rows':[dict(x) for x in cur.fetchall()] if cur.description else [],'changes':cur.rowcount}
 except Exception as e:r={'error':str(e)}
 print(json.dumps(r),flush=True)
