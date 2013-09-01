from twisted.internet import protocol, reactor

class Echo(protocol.Protocol):
  def dataReceived(self, data):
    print data
    self.transport.loseConnection()

class EchoFactory(protocol.Factory):
  def buildProtocol(self, addr):
    return Echo()

reactor.listenTCP(8888, EchoFactory())
reactor.run()
