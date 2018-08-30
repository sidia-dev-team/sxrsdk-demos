/*
 * Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.gearvrf.arpet.manager.connection;

import android.support.annotation.NonNull;

import org.gearvrf.arpet.connection.Connection;
import org.gearvrf.arpet.connection.ConnectionManager;
import org.gearvrf.arpet.connection.Device;
import org.gearvrf.arpet.connection.ManagerState;
import org.gearvrf.arpet.connection.Message;
import org.gearvrf.arpet.connection.OnConnectionListener;
import org.gearvrf.arpet.connection.OnMessageListener;
import org.gearvrf.arpet.connection.exception.ConnectionException;
import org.gearvrf.arpet.connection.socket.IncomingSocketConnectionThread;
import org.gearvrf.arpet.connection.socket.SocketConnectionThreadFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseSocketConnectionManager implements ConnectionManager, OnConnectionListener, OnMessageListener {

    protected final String TAG = getClass().getSimpleName();

    @ManagerState
    private int mState = ManagerState.IDLE;
    private List<Connection> mConnections = new ArrayList<>();
    private SocketConnectionThreadFactory mSocketConnectionThreadFactory;
    private IncomingSocketConnectionThread mIncomingSocketConnection;

    private int mTotalConnectionsDesired, mTotalConnectionsFailed;

    public BaseSocketConnectionManager() {
        mSocketConnectionThreadFactory = createSocketConnectionThreadFactory();
    }

    @Override
    public synchronized void connectToDevices(@NonNull OnMessageListener messageListener, @NonNull Device... devices) {
        if (stateIs(ManagerState.IDLE) && devices.length > 0) {
            mTotalConnectionsDesired = devices.length;
            mTotalConnectionsFailed = 0;
            setState(ManagerState.CONNECTING_TO_REMOTE);
            for (Device device : devices) {
                mSocketConnectionThreadFactory.createOutgoingSocketConnectionThread(
                        device, this, this).start();
            }
        }
    }

    @Override
    public synchronized void startConnectionListener(@NonNull OnMessageListener messageListener) {
        if (stateIs(ManagerState.IDLE)) {
            setState(ManagerState.LISTENING_TO_CONNECTIONS);
            mIncomingSocketConnection = mSocketConnectionThreadFactory
                    .createIncomingSocketConnectionThread(this, this);
            mIncomingSocketConnection.start();
        }
    }

    @Override
    public synchronized void stopConnectionListener() {
        if (stateIs(ManagerState.LISTENING_TO_CONNECTIONS)) {
            mIncomingSocketConnection.cancel();
            mIncomingSocketConnection = null;
            if (getTotalConnected() == 0) {
                setState(ManagerState.IDLE);
            } else {
                setState(ManagerState.CONNECTED);
            }
        }
    }

    @Override
    public synchronized void sendMessage(Message message) {
        if (stateIs(ManagerState.CONNECTED)) {
            for (Connection connection : mConnections) {
                connection.write(message);
            }
        }
    }

    @Override
    public synchronized List<Device> getConnectedDevices() {
        List<Device> devices = new ArrayList<>(mConnections.size());
        for (Connection connection : mConnections) {
            devices.add(connection.getRemoteDevice());
        }
        return devices;
    }

    @Override
    public synchronized int getState() {
        return mState;
    }

    @Override
    public synchronized int getTotalConnected() {
        return mConnections.size();
    }

    @Override
    public synchronized void disconnect() {
        if (stateIs(ManagerState.CONNECTED)) {
            try {
                for (Connection connection : mConnections) {
                    connection.close();
                }
                mConnections.clear();
                setState(ManagerState.IDLE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected abstract SocketConnectionThreadFactory createSocketConnectionThreadFactory();

    private void setState(@ManagerState int mState) {
        this.mState = mState;
    }

    private boolean stateIs(@ManagerState int state) {
        return getState() == state;
    }

    // CONNECTION LISTENER METHODS

    @Override
    public synchronized void onConnectionEstablished(Connection connection) {
        mConnections.add(connection);
        if (checkOutgoingConnectionsFinished()) {
            setState(ManagerState.CONNECTED);
        }
    }

    @Override
    public void onConnectionFailure(ConnectionException error) {
        synchronized (this) {
            if (checkOutgoingConnectionsFinished()) {
                if (getTotalConnected() == 0) {
                    setState(getTotalConnected() == 0 ? ManagerState.IDLE : ManagerState.CONNECTED);
                }
            } else if (stateIs(ManagerState.LISTENING_TO_CONNECTIONS)) {
                // When failed while accepting the first connection then set final state
                // since the listener thread is stopped on any error
                if (getTotalConnected() == 0) {
                    setState(getTotalConnected() == 0 ? ManagerState.IDLE : ManagerState.CONNECTED);
                }
            }
        }
        error.printStackTrace();
    }

    @Override
    public void onConnectionLost(Connection connection, ConnectionException error) {
        synchronized (this) {
            mConnections.remove(connection);
            if (mConnections.size() == 0) {
                setState(ManagerState.IDLE);
            }
        }
        if (error != null) {
            error.printStackTrace();
        }
    }

    private boolean checkOutgoingConnectionsFinished() {
        int connectionsSuccessful = mConnections.size();
        return stateIs(ManagerState.CONNECTING_TO_REMOTE)
                && (connectionsSuccessful + mTotalConnectionsFailed) == mTotalConnectionsDesired;
    }

}
