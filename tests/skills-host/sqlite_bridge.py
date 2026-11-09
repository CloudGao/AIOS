import sys,json,sqlite3
c=sqlite3.connect(':memory:');c.row_factory=sqlite3.Row
for line in sys.stdin:
 try:
  q=json.loads(line);cur=c.execute(q['sql'],q['args']);rows=[dict(x) for x in cur.fetchall()] if cur.description else [];c.commit();r={'rows':rows,'changes':cur.rowcount}
 except Exception as e:r={'error':str(e)}
 print(json.dumps(r),flush=True)
