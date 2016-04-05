import os
import json
import psycopg2
from sqlalchemy import *
from sqlalchemy.pool import NullPool
from flask import Flask, request, render_template, g, redirect, Response, session,make_response,url_for
import time
from datetime import datetime
from datetime import timedelta


tmpl_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'templates')
app = Flask(__name__, template_folder=tmpl_dir)
app.jinja_env.trim_blocks = True
app.jinja_env.lstrip_blocks = True
app.secret_key = "behappy"


DATABASEURI = "postgresql://cl3391:LURCDH@w4111db.eastus.cloudapp.azure.com/cl3391"

engine = create_engine(DATABASEURI)

@app.before_request
def before_request():

  try:
    g.conn = engine.connect()
  except:
    print "uh oh, problem connecting to database"
    import traceback; traceback.print_exc()
    g.conn = None

@app.teardown_request
def teardown_request(exception):
  try:
    g.conn.close()
  except Exception as e:
    pass


@app.route('/')
def index():
  now_time_hour = time.strftime("%H",time.localtime(time.time()))
  int_hour = int(now_time_hour)
  return render_template("index.html", hour= int_hour)


@app.route('/driver',methods=['GET', 'POST'])
def driver():
  if request.method == 'GET':
    return render_template('driver.html',wrong ='' )
  if request.method == 'POST':
    cursor = g.conn.execute("SELECT driver_id,driver_name,driver_password FROM driver_has WHERE driver_id = '{}' and driver_password = '{}'".format(request.form['driver_id'],text(request.form['driver_password'])))
    for row in cursor:
      session['driver_id'] = int(row['driver_id'])
      session['driver_name'] = str(row['driver_name'])
      return redirect(url_for('driver_Welcome'))
    else:
      return render_template('driver.html',wrong ='Invalid ID or Password! Try again!' )

@app.route('/passenger',methods=['GET', 'POST'])
def passenger():
  if request.method == 'GET':
    return render_template('passenger.html',wrong ='' )
  if request.method == 'POST':
    cursor = g.conn.execute("SELECT pass_id,pass_name,pass_password FROM passenger WHERE pass_id = '{}' and pass_password = '{}'".format(request.form['pass_id'],text(request.form['pass_password'])))
    for row in cursor:
      session['pass_id'] = int(row['pass_id'])
      session['pass_name'] = str(row['pass_name'])
      return redirect(url_for('passenger_page'))
    else:
      return render_template('passenger.html',wrong ='Invalid ID or Password! Try again!' )


@app.route('/driver_register',methods=['GET', 'POST'])
def driver_register():
  if request.method == 'GET':
    return render_template("driver_register.html",id = '')
  if request.method == 'POST':
    driver_phone = str(request.form['driver_phone'])
    if (not driver_phone.isdigit()) or (len(driver_phone)!= 10):
      return render_template('driver_register.html',id ='Invalid Input!' )
   #get initial ID number
    cursor = g.conn.execute("SELECT max(driver_id) FROM driver_has;")
    for row in cursor:
      initial = int(row[0])+1

    name = str(request.form['driver_name'])
    license = str(request.form['license'])
    brand_id = int(request.form['brand'])
    driver_special = str(request.form['special'])
    driver_password = str(request.form['driver_password'])
    
    driver_id = initial
    score = 0.0
    q = "INSERT INTO driver_has VALUES (%s,%s,%s,%s,%s,%s,%s,%s);"
    try:
      g.conn.execute(q,(driver_id,name,license,score,driver_special,brand_id,driver_password,driver_phone))
      return render_template('driver_register.html',id ='Your id is: ' + str(initial))   
    except:
      return render_template('driver_register.html',id ='Invalid Input!' )


@app.route('/passenger_register',methods=['GET', 'POST'])
def passenger_register():
  if request.method == 'GET':
    return render_template("passenger_register.html",id = '')
  if request.method == 'POST':
   #get initial ID number
    cursor = g.conn.execute("SELECT max(pass_id) FROM passenger;")
    for row in cursor:
      initial = int(row[0])+1

    name = str(request.form['pass_name'])
    pass_special = str(request.form['special'])
    pass_password = str(request.form['pass_password'])
    pass_id = initial
    q = "INSERT INTO passenger VALUES (%s,%s,%s,%s);"
    try:
      g.conn.execute(q,(pass_id,name,pass_special,pass_password))
      return render_template('passenger_register.html',id ='Your id is: ' + str(initial))   
    except:
      return render_template('passenger_register.html',id ='Invalid Input!' )



@app.route('/driver_logout')
def driver_logout():
  session.pop('driver_id', None)
  session.pop('driver_name', None)
  return redirect(url_for('driver'))

@app.route('/pass_logout')
def pass_logout():
  session.pop('pass_id', None)
  session.pop('pass_name', None)
  return redirect(url_for('passenger'))

@app.route('/driver_Welcome')
def driver_Welcome():
  if 'driver_id' in session:
    cursor = g.conn.execute("SELECT loc_name,popularity FROM location;")
    return render_template('driver_Welcome.html',driver_name=session['driver_name'],location= cursor)
  else:
    return redirect(url_for('driver'))

@app.route('/passenger_page')
def passenger_page():
  if 'pass_id' in session:
    return render_template('passenger_page.html',pass_name=session['pass_name'])
  else:
    return redirect(url_for('passenger'))




@app.route('/driver_history',methods=['GET', 'POST'])
def driver_history():
  if 'driver_id' in session:
    cursor = g.conn.execute("SELECT pass_name,start_time FROM trip_take T,passenger P WHERE T.pass_id=P.pass_id and driver_id='{}' ORDER BY start_time DESC LIMIT 5".format(session['driver_id']))
    if request.method == 'POST':
      start_loc = int(request.form['start_loc'])
      end_loc = int(request.form['end_loc'])
      cursor2 = g.conn.execute("SELECT route_id FROM route WHERE from_loc='{}' and to_loc='{}'".format(start_loc,end_loc))
      for row in cursor2:
        route_id=row[0]
      start_time = str(request.form['start_time'])
      end_time = str(request.form['end_time'])
      driver_id = session['driver_id']
      q = "INSERT INTO driverealtime VALUES (%s,%s,%s,%s);"
      try:    
         g.conn.execute(q,(driver_id,route_id,start_time,end_time))
      except:
        return redirect(url_for('driver_Welcome'))
    status=g.conn.execute("SELECT pass_id,departure,route_id,end_time FROM driverealtime WHERE driver_id='{}'".format(session['driver_id']))
    ##calculate latesst start_time for this driver
    rid=None
    resv=None
    depart=None
    end_t=None
    departure=''
    for row in status:
      resv=row[0]
      depart=row[1]
      rid=row[2]
      end_t=row[3]
    if rid:
      cur1 = g.conn.execute("SELECT time_minute FROM route WHERE route_id='{}'".format(rid))
      for row in cur1:
        approximate_time=int(row[0]*60)
      if not approximate_time:
        cur3 = g.conn.execute("SELECT distance_mile FROM route WHERE route_id='{}'".format(rid))
        for row in cur3:
          mile = row[0]
          approximate_time = int(mile*180)
      app_time=timedelta(seconds=approximate_time)
      end_t = end_t-app_time
      departure = str(end_t)
      if resv:
        if depart:
          cursor3 = g.conn.execute("SELECT L1.loc_name,L2.loc_name,start_time,end_time,pass_name,departure FROM driverealtime T,route R,location L1,location L2,passenger P WHERE T.pass_id=P.pass_id and T.route_id=R.route_id and R.from_loc=L1.loc_id and R.to_loc=L2.loc_id and driver_id='{}'".format(session['driver_id']))
          state='D'
        else:
          cursor3 = g.conn.execute("SELECT L1.loc_name,L2.loc_name,start_time,end_time,pass_name,booked_time FROM driverealtime T,route R,location L1,location L2,passenger P WHERE T.pass_id=P.pass_id and T.route_id=R.route_id and R.from_loc=L1.loc_id and R.to_loc=L2.loc_id and driver_id='{}'".format(session['driver_id']))
          state='R'
      else:
        cursor3 = g.conn.execute("SELECT L1.loc_name,L2.loc_name,start_time,end_time FROM driverealtime T,route R,location L1,location L2 WHERE T.route_id=R.route_id and R.from_loc=L1.loc_id and R.to_loc=L2.loc_id and driver_id='{}'".format(session['driver_id']))
        state='F'
      for row3 in cursor3:
        return render_template('driver_history.html',has_started = True, start_time = departure,driver_name=session['driver_name'], driver_unfinished_trip=row3,driver_finished_list=cursor,state=state)
    return render_template('driver_history.html',has_started = False, start_time = departure,driver_name=session['driver_name'], driver_unfinished_trip=None,driver_finished_list=cursor,state='F')

  #match for the session part
  else:
    return redirect(url_for('driver'))

@app.route('/driver_2history',methods=['GET', 'POST'])
def driver_2history():
  if 'driver_id' in session:
    start_end = (request.form['start_end'])
    now_time = time.strftime("%Y-%m-%d %H:%M",time.localtime(time.time()))
    if start_end=='S':
      try:
        g.conn.execute("UPDATE driverealtime SET departure='{}' WHERE driver_id='{}'".format(now_time,session['driver_id']))
        cursor3 = g.conn.execute("SELECT L1.loc_name,L2.loc_name,start_time,end_time,pass_name,departure FROM driverealtime T,route R,location L1,location L2,passenger P WHERE T.pass_id=P.pass_id and T.route_id=R.route_id and R.from_loc=L1.loc_id and R.to_loc=L2.loc_id and driver_id='{}'".format(session['driver_id']))
        cursor = g.conn.execute("SELECT pass_name,start_time FROM trip_take T,passenger P WHERE T.pass_id=P.pass_id and driver_id='{}' ORDER BY start_time DESC LIMIT 5".format(session['driver_id']))
        for row3 in cursor3:
          return render_template('driver_history.html',has_started = False,driver_name=session['driver_name'], driver_unfinished_trip=row3,driver_finished_list=cursor,state='D')
      except:
        return redirect(url_for('driver_history'))
    else:
      try:
        depart=g.conn.execute("SELECT departure,pass_id,route_id FROM driverealtime WHERE driver_id='{}'".format(session['driver_id']))
        for row in depart:
          depart_time=row[0]
          pass_id=row[1]
          route_id=row[2]
        time_now=datetime.strptime(now_time,'%Y-%m-%d %H:%M')
        trip_time=((time_now-depart_time).seconds)/60+((time_now-depart_time).days)*24*60
        q = "INSERT INTO trip_take VALUES (%s,%s,%s,%s,%s,%s,%s);"
        #get initial ID number
        cursor4 = g.conn.execute("SELECT max(trip_id) FROM trip_take;")
        for row in cursor4:
          initial = int(row[0])+1
        g.conn.execute(q,(initial,pass_id,session['driver_id'],depart_time,0.0,trip_time,route_id))
        cursor5 = g.conn.execute("SELECT avg(trip_time_minute) FROM trip_take WHERE route_id='{}'".format(route_id))
        for row in cursor5:
          g.conn.execute("UPDATE route SET time_minute='{}' WHERE route_id='{}'".format(int(row[0]),route_id))
        g.conn.execute("DELETE FROM driverealtime WHERE driver_id='{}'".format(session['driver_id']))
        cursor = g.conn.execute("SELECT pass_name,start_time FROM trip_take T,passenger P WHERE T.pass_id=P.pass_id and driver_id='{}' ORDER BY start_time DESC LIMIT 5".format(session['driver_id']))
        return render_template('driver_history.html',has_started = False,driver_name=session['driver_name'], driver_unfinished_trip=None,driver_finished_list=cursor,state='F')
      except:
        return redirect(url_for('driver_history'))
  #match for the session part
  else:
    return redirect(url_for('driver'))


@app.route('/passenger_history',methods=['GET', 'POST'])
def passenger_history():
  if 'pass_id' in session:
    cursor = g.conn.execute("SELECT driver_name,driver_phone,start_time,T.score,trip_id FROM trip_take T,driver_has D WHERE T.driver_id=D.driver_id and pass_id='{}' ORDER BY start_time DESC LIMIT 5".format(session['pass_id']))
    if request.method == 'POST':
      driver_id = int(request.form['driver_selected'])
      booked_time=session['booked_time']
      pass_id = session['pass_id']
      try:    
        g.conn.execute("UPDATE driverealtime SET pass_id='{}',booked_time='{}' WHERE driver_id='{}'".format(pass_id,booked_time,driver_id))
        session.pop('booked_time', None)
      except:
        return redirect(url_for('passenger_page'))
    cursor3 = g.conn.execute("SELECT driver_name,license,driver_phone,L1.loc_name,L2.loc_name,booked_time,round((cost_mile*(distance_mile+1))::numeric,2),time_minute FROM driverealtime T,driver_has D,route R,location L1,location L2, brand B WHERE T.driver_id=D.driver_id and T.route_id=R.route_id and R.from_loc=L1.loc_id and R.to_loc=L2.loc_id and D.brand_id=B.brand_id and pass_id='{}'".format(session['pass_id']))
    return render_template('passenger_history.html',passenger_name=session['pass_name'], passenger_unfinished_list=cursor3,passenger_finished_list=cursor)

  #match for the session part
  else:
    return redirect(url_for('passenger'))

@app.route('/passenger_2history',methods=['GET', 'POST'])
def passenger_2history():
  if 'pass_id' in session:
    if request.method == 'POST':
      array = request.form['score']
      trip = array.split(',')
      try:    
        g.conn.execute("UPDATE trip_take SET score='{}' WHERE trip_id='{}'".format(int(trip[1]),int(trip[0])))
        cursor1 = g.conn.execute("SELECT driver_id from trip_take where trip_id='{}'".format(int(trip[0])))
        for row in cursor1:
          driver_id=int(row[0])
        cursor2 = g.conn.execute("SELECT avg(score) FROM trip_take WHERE driver_id ='{}'".format(driver_id))
        for row in cursor2:
          score = round(float(row[0]),1)
          g.conn.execute("UPDATE driver_has SET score='{}' WHERE driver_id='{}'".format(float(score),driver_id))
      except:
        return redirect(url_for('passenger_history'))
    cursor = g.conn.execute("SELECT driver_name,driver_phone,start_time,T.score,trip_id FROM trip_take T,driver_has D WHERE T.driver_id=D.driver_id and pass_id='{}' ORDER BY start_time DESC LIMIT 5".format(session['pass_id']))
    cursor3 = g.conn.execute("SELECT driver_name,license,driver_phone,L1.loc_name,L2.loc_name,booked_time,round((cost_mile*(distance_mile+1))::numeric,2),time_minute FROM driverealtime T,driver_has D,route R,location L1,location L2, brand B WHERE T.driver_id=D.driver_id and T.route_id=R.route_id and R.from_loc=L1.loc_id and R.to_loc=L2.loc_id and D.brand_id=B.brand_id and pass_id='{}'".format(session['pass_id']))
    return render_template('passenger_history.html',passenger_name=session['pass_name'], passenger_unfinished_list=cursor3,passenger_finished_list=cursor)

  #match for the session part
  else:
    return redirect(url_for('passenger'))






  
@app.route('/passenger_select',methods=['GET', 'POST'])
def passenger_select():
  if 'pass_id' in session:
    if request.method == 'POST':
      start_loc = int(request.form['loc1'])
      end_loc = int(request.form['loc2'])
      cursor = g.conn.execute("SELECT route_id FROM route WHERE from_loc='{}' and to_loc='{}'".format(start_loc,end_loc))
      for row in cursor:
        route_id=row[0]
        cursor1 = g.conn.execute("SELECT time_minute FROM route WHERE route_id='{}'".format(route_id))
        for row in cursor1:
          approximate_time=int(row[0]*60)
        app_time=timedelta(seconds=approximate_time)
        depart_string = str(request.form['departure'])
        try:
          time_now=datetime.strptime(depart_string,'%Y-%m-%d %H:%M')
        except:
          return redirect(url_for('passenger_page'))
        time_now = time_now+app_time
        departure = str(time_now)
        try:
          cursor2 = g.conn.execute("SELECT T.driver_id,driver_name,score,driver_special,brand_name,volume,cost_mile FROM driverealtime T,driver_has D,brand B WHERE T.driver_id=D.driver_id and D.brand_id=B.brand_id and route_id='{}' and start_time<='{}' and end_time>='{}' and pass_id is null".format(route_id,depart_string,departure))
          cursor3 = g.conn.execute("SELECT * FROM driverealtime WHERE route_id='{}' and start_time<='{}' and end_time>='{}' and pass_id is null".format(route_id,depart_string,departure))
          has_driver=False
          for rows in cursor3:
            has_driver = True
            session['booked_time']=depart_string
          return render_template('passenger_select.html',pass_name=session['pass_name'], driver_available=cursor2, has_driver=has_driver)
        except:
          return redirect(url_for('passenger_page'))
      return redirect(url_for('passenger_page'))
    return redirect(url_for('passenger_page'))
  return redirect(url_for('passenger'))

@app.route('/real_time_msg',methods=['GET', 'POST'])
def real_time_msg():
  if 'driver_id' in session:
    #cursor1 is for street_available 
    cursor1 = g.conn.execute('SELECT street_id,street_name from Street')

    if request.method == 'POST':
      comment = str(request.form['message'])
      street_id = int(request.form['street_selected'])
      driver_id = int(session['driver_id'])
      now_time = time.strftime("%Y-%m-%d %H:%M",time.localtime(time.time()))
      #not sure whether msg_time is fine
      q = "INSERT INTO MsgRealTime VALUES (%s,%s,%s,%s);"  
      try:
        #one driver can only send a specific street one corresponding message, thus it should try to delete first
        g.conn.execute("DELETE from MsgRealTime where driver_id = '{}' and street_id = '{}'".format(driver_id,street_id))
        #then, try to insert the new corresponding message
        g.conn.execute(q,(driver_id,street_id,now_time,comment))

      except:
        return redirect(url_for('driver_Welcome'))
        #cursor2 is for msg_real_time
    cursor2 = g.conn.execute('SELECT driver_name, street_name, comment, msg_time from driver_has D,MsgRealTime MR, street S where s.street_id = MR.street_id and MR.driver_id = D.driver_id')
    return render_template('real_time_message.html', street_available = cursor1, msg_real_time = cursor2, driver_name = session['driver_name'])



  #corresponding to the session part 

  return redirect(url_for('driver'))






if __name__ == '__main__':
  import click

  @click.command()
  @click.option('--debug', is_flag=True)
  @click.option('--threaded', is_flag=True)
  @click.argument('HOST', default='0.0.0.0')
  @click.argument('PORT', default=8111, type=int)
  def run(debug, threaded, host, port):
    """
    This function handles command line parameters.
    Run the server using

        python server.py

    Show the help text using

        python server.py --help

    """

    HOST, PORT = host, port
    print "running on %s:%d" % (HOST, PORT)
    app.run(host=HOST, port=PORT, debug=debug, threaded=threaded)


  run()