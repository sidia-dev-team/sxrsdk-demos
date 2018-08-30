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

package org.gearvrf.arpet.manager.connection.bluetooth;

import android.support.annotation.NonNull;

import org.gearvrf.arpet.connection.Connection;
import org.gearvrf.arpet.connection.OnConnectionListener;
import org.gearvrf.arpet.connection.OnMessageListener;
import org.gearvrf.arpet.connection.socket.OutgoingSocketConnectionThread;

import java.io.IOException;

public class BTOutgoingSocketConnectionThread extends OutgoingSocketConnectionThread<BTSocket> {

    private BTDevice mDevice;
    private OnMessageListener mMessageListener;

    BTOutgoingSocketConnectionThread(
            @NonNull BTDevice device,
            @NonNull OnMessageListener messageListener,
            @NonNull OnConnectionListener connectionListener) {

        super(connectionListener);
        this.mDevice = device;
        this.mMessageListener = messageListener;
    }

    @Override
    protected BTSocket getSocket() throws IOException {
        return mDevice.createSocket(BTConstants.SOCKET_SERVER_UUID);
    }

    @Override
    protected Connection createConnection(BTSocket socket) {
        return new BTOngoingSocketConnectionThread(socket, mMessageListener, mOnConnectionListener);
    }
}
