/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.jaegertracing.thrift.internal.reporters.protocols;

import io.jaegertracing.agent.thrift.Agent;
import io.jaegertracing.thriftjava.Batch;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TTransportException;

public class TestTServer implements Runnable {
  TServer server;
  InMemorySpanServerHandler handler;
  ThriftUdpServerTransport transport;

  public TestTServer(int port) throws SocketException, UnknownHostException, TTransportException {
    handler = new InMemorySpanServerHandler();
    transport = new ThriftUdpServerTransport(port);
    server =
        new TSimpleServer(
            new TServer.Args(transport)
                .protocolFactory(new TCompactProtocol.Factory())
                .processor(new Agent.Processor<>(handler)));
  }

  public int getPort() {
    return transport.getPort();
  }

  @Override
  public void run() {
    server.serve();
  }

  public void close() {
    server.stop();
  }

  public Batch getBatch(int expectedSpans, int timeout) throws Exception {

    Batch batch = new Batch().setSpans(new ArrayList<>());
    long expire = timeout + System.currentTimeMillis();
    while (System.currentTimeMillis() < expire) {
      Batch receivedBatch = handler.getBatch();
      if (receivedBatch.getSpans() != null) {
        batch.getSpans().addAll(receivedBatch.getSpans());
        batch.setProcess(receivedBatch.getProcess());
      }

      if (batch.getSpans().size() >= expectedSpans) {
        return batch;
      }

      Thread.sleep(1);
    }

    return batch;
  }
}
