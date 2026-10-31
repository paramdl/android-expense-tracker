"""Seeds a large transaction set into a pulled copy of the app database.

Used to benchmark the dashboard queries under a realistic multi-year ledger.
Usage:  python tools/seed_bench.py <db-path> <transaction-count>
"""
import sqlite3
import sys
import random
import time
import datetime

db_path = sys.argv[1]
target = int(sys.argv[2]) if len(sys.argv) > 2 else 20000

con = sqlite3.connect(db_path)
cur = con.cursor()

cats = [(i, t) for i, t in cur.execute("select id,type from category")]
exp_cats = [i for i, t in cats if t == "EXPENSE"]
inc_cats = [i for i, t in cats if t == "INCOME"]
accs = [i for (i,) in cur.execute("select id from account")]

people = [i for (i,) in cur.execute("select id from person")]
if not people:
    for name in ("Personal", "Home", "Family", "Savings", "Friends"):
        cur.execute("insert into person(name,note) values(?,'')", (name,))
        people.append(cur.lastrowid)

merchants = ["Swiggy", "Zomato", "Amazon", "Flipkart", "BigBasket", "DMart", "IKEA",
             "Uber", "Indian Oil", "Netflix", "Airtel", "Apollo Pharmacy",
             "BookMyShow", "MakeMyTrip", "Myntra", "Pepperfry", "Blinkit", "Zepto"]

random.seed(11)
cur.execute("delete from tx")

now = datetime.datetime(2026, 8, 24, 19, 0, 0)
span_days = 365 * 3          # three years of history
rows = []
for _ in range(target):
    is_income = random.random() < 0.06
    d = now - datetime.timedelta(days=random.randint(0, span_days),
                                 hours=random.randint(0, 23),
                                 minutes=random.randint(0, 59))
    rows.append((
        "INCOME" if is_income else "EXPENSE",
        round(random.uniform(50, 9000), 2),
        int(time.mktime(d.timetuple()) * 1000),
        random.choice(inc_cats if is_income else exp_cats),
        random.choice(people) if random.random() < 0.6 else None,
        random.choice(accs),
        random.choice(merchants),
        "",
        "SMS" if random.random() < 0.4 else "MANUAL",
        int(time.time() * 1000),
    ))

cur.executemany(
    "insert into tx(type,amount,date,category_id,person_id,account_id,merchant,note,source,created_at)"
    " values(?,?,?,?,?,?,?,?,?,?)", rows)
con.commit()
print("seeded", cur.execute("select count(*) from tx").fetchone()[0], "transactions")
print("span  ", cur.execute("select min(date),max(date) from tx").fetchone())
con.close()
