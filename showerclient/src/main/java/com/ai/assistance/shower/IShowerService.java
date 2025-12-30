package com.ai.assistance.shower;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IShowerService extends IInterface {

    void ensureDisplay(int width, int height, int dpi, int bitrateKbps) throws RemoteException;

    void destroyDisplay() throws RemoteException;

    void launchApp(String packageName) throws RemoteException;

    void tap(float x, float y) throws RemoteException;

    void swipe(float x1, float y1, float x2, float y2, long durationMs) throws RemoteException;

    void touchDown(float x, float y) throws RemoteException;

    void touchMove(float x, float y) throws RemoteException;

    void touchUp(float x, float y) throws RemoteException;

    void injectKey(int keyCode) throws RemoteException;

    byte[] requestScreenshot() throws RemoteException;

    int getDisplayId() throws RemoteException;

    void setVideoSink(IBinder sink) throws RemoteException;

    abstract class Stub extends Binder implements IShowerService {

        private static final String DESCRIPTOR = "com.ai.assistance.shower.IShowerService";
        static final int TRANSACTION_ensureDisplay = IBinder.FIRST_CALL_TRANSACTION;
        static final int TRANSACTION_destroyDisplay = IBinder.FIRST_CALL_TRANSACTION + 1;
        static final int TRANSACTION_launchApp = IBinder.FIRST_CALL_TRANSACTION + 2;
        static final int TRANSACTION_tap = IBinder.FIRST_CALL_TRANSACTION + 3;
        static final int TRANSACTION_swipe = IBinder.FIRST_CALL_TRANSACTION + 4;
        static final int TRANSACTION_touchDown = IBinder.FIRST_CALL_TRANSACTION + 5;
        static final int TRANSACTION_touchMove = IBinder.FIRST_CALL_TRANSACTION + 6;
        static final int TRANSACTION_touchUp = IBinder.FIRST_CALL_TRANSACTION + 7;
        static final int TRANSACTION_injectKey = IBinder.FIRST_CALL_TRANSACTION + 8;
        static final int TRANSACTION_requestScreenshot = IBinder.FIRST_CALL_TRANSACTION + 9;
        static final int TRANSACTION_getDisplayId = IBinder.FIRST_CALL_TRANSACTION + 10;
        static final int TRANSACTION_setVideoSink = IBinder.FIRST_CALL_TRANSACTION + 11;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IShowerService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IShowerService) {
                return (IShowerService) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_ensureDisplay: {
                    data.enforceInterface(DESCRIPTOR);
                    int width = data.readInt();
                    int height = data.readInt();
                    int dpi = data.readInt();
                    int bitrate = data.readInt();
                    ensureDisplay(width, height, dpi, bitrate);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_destroyDisplay: {
                    data.enforceInterface(DESCRIPTOR);
                    destroyDisplay();
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_launchApp: {
                    data.enforceInterface(DESCRIPTOR);
                    String pkg = data.readString();
                    launchApp(pkg);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_tap: {
                    data.enforceInterface(DESCRIPTOR);
                    float x = data.readFloat();
                    float y = data.readFloat();
                    tap(x, y);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_swipe: {
                    data.enforceInterface(DESCRIPTOR);
                    float x1 = data.readFloat();
                    float y1 = data.readFloat();
                    float x2 = data.readFloat();
                    float y2 = data.readFloat();
                    long duration = data.readLong();
                    swipe(x1, y1, x2, y2, duration);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_touchDown: {
                    data.enforceInterface(DESCRIPTOR);
                    float x = data.readFloat();
                    float y = data.readFloat();
                    touchDown(x, y);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_touchMove: {
                    data.enforceInterface(DESCRIPTOR);
                    float x = data.readFloat();
                    float y = data.readFloat();
                    touchMove(x, y);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_touchUp: {
                    data.enforceInterface(DESCRIPTOR);
                    float x = data.readFloat();
                    float y = data.readFloat();
                    touchUp(x, y);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_injectKey: {
                    data.enforceInterface(DESCRIPTOR);
                    int keyCode = data.readInt();
                    injectKey(keyCode);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_requestScreenshot: {
                    data.enforceInterface(DESCRIPTOR);
                    byte[] result = requestScreenshot();
                    reply.writeNoException();
                    reply.writeByteArray(result);
                    return true;
                }
                case TRANSACTION_getDisplayId: {
                    data.enforceInterface(DESCRIPTOR);
                    int id = getDisplayId();
                    reply.writeNoException();
                    reply.writeInt(id);
                    return true;
                }
                case TRANSACTION_setVideoSink: {
                    data.enforceInterface(DESCRIPTOR);
                    IBinder sink = data.readStrongBinder();
                    setVideoSink(sink);
                    reply.writeNoException();
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static final class Proxy implements IShowerService {

            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public void ensureDisplay(int width, int height, int dpi, int bitrateKbps) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(width);
                    data.writeInt(height);
                    data.writeInt(dpi);
                    data.writeInt(bitrateKbps);
                    remote.transact(TRANSACTION_ensureDisplay, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void destroyDisplay() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_destroyDisplay, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void launchApp(String packageName) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(packageName);
                    remote.transact(TRANSACTION_launchApp, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void tap(float x, float y) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeFloat(x);
                    data.writeFloat(y);
                    remote.transact(TRANSACTION_tap, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void swipe(float x1, float y1, float x2, float y2, long durationMs) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeFloat(x1);
                    data.writeFloat(y1);
                    data.writeFloat(x2);
                    data.writeFloat(y2);
                    data.writeLong(durationMs);
                    remote.transact(TRANSACTION_swipe, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void touchDown(float x, float y) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeFloat(x);
                    data.writeFloat(y);
                    remote.transact(TRANSACTION_touchDown, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void touchMove(float x, float y) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeFloat(x);
                    data.writeFloat(y);
                    remote.transact(TRANSACTION_touchMove, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void touchUp(float x, float y) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeFloat(x);
                    data.writeFloat(y);
                    remote.transact(TRANSACTION_touchUp, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void injectKey(int keyCode) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(keyCode);
                    remote.transact(TRANSACTION_injectKey, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public byte[] requestScreenshot() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_requestScreenshot, data, reply, 0);
                    reply.readException();
                    return reply.createByteArray();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public int getDisplayId() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_getDisplayId, data, reply, 0);
                    reply.readException();
                    return reply.readInt();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void setVideoSink(IBinder sink) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStrongBinder(sink);
                    remote.transact(TRANSACTION_setVideoSink, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
