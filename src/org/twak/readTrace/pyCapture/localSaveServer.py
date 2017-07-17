'''
Created on 18 Nov 2016

@author: twak
'''

from BaseHTTPServer import BaseHTTPRequestHandler,HTTPServer
from SocketServer import ThreadingMixIn
import threading
import argparse
import re
import cgi
import json
from os import listdir
import os
 
class LocalData(object):
  records = {}
 
PATH = "/home/twak/Desktop/panos/" 
 
class HTTPRequestHandler(BaseHTTPRequestHandler):
  def do_POST(self):
    print "post"
    print self.path
    if None != re.search('/save*', self.path):
      print "thats save"
      ctype, pdict = cgi.parse_header(self.headers.getheader('content-type'))
      print ctype
      length = int(self.headers.getheader('content-length'))
      print length
      read = self.rfile.read(length)
      data = json.loads( read )
      print data["filename"]
      print len ( data["data"] )
      
      with open(PATH+data["filename"], "wb") as fh:
        fh.write(data["data"][22:].decode('base64'))
#         fh.write(data["data"].decode('base64'))
      
      self.send_response(200)
      self.send_header('Content-Type', 'text/plain')
      self.send_header("Access-Control-Allow-Origin", "*")
      self.end_headers()
      
    elif None != re.search('/query/*', self.path):
#       ctype, pdict = cgi.parse_header(self.headers.getheader('content-type'))
      length = int(self.headers.getheader('content-length'))
      key = self.rfile.read(length)
      
      print key
      
      result = False
      
      if not os.path.exists(PATH):
          os.makedirs(PATH)
      
      for f in listdir(PATH):
          if f.startswith(key) or f.endswith(key+".jpg"):
              result = True
              break
          
      print result
      
      self.send_response(200)
      self.send_header('Content-Type', 'text/plain')
      self.send_header("Access-Control-Allow-Origin", "*")
      self.end_headers()      
      
      self.wfile.write(str ( result) )
          
    else:
      self.send_response(403)
      self.send_header('Content-Type', 'application/json')
      self.end_headers()
    return

  def do_OPTIONS(self):
    print "options"
    self.send_header("Access-Control-Allow-Origin", "*")
    self.send_response(200) 
    self.end_headers()
  
class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
  allow_reuse_address = True
 
  def shutdown(self):
    self.socket.close()
    HTTPServer.shutdown(self)
 
class SimpleHttpServer():
  def __init__(self, ip, port):
    self.server = ThreadedHTTPServer((ip,port), HTTPRequestHandler)
 
  def start(self):
    self.server_thread = threading.Thread(target=self.server.serve_forever)
    self.server_thread.daemon = True
    self.server_thread.start()
 
  def waitForThread(self):
    self.server_thread.join()
 
  def addRecord(self, recordID, jsonEncodedRecord):
    LocalData.records[recordID] = jsonEncodedRecord
 
  def stop(self):
    self.server.shutdown()
    self.waitForThread()
 
if __name__=='__main__':
  parser = argparse.ArgumentParser(description='HTTP Server')
  parser.add_argument('port', type=int, help='Listening port for HTTP Server')
  parser.add_argument('ip', help='HTTP Server IP')
  args = parser.parse_args()
 
  server = SimpleHttpServer(args.ip, args.port)
  print 'HTTP Server Running...........'
  server.start()
  server.waitForThread()