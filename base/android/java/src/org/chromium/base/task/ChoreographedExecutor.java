// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base.task;

import org.chromium.base.ThreadUtils;

import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;

import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.Executor;

public class ChoreographedExecutor implements Executor {
    private final Choreographer mChoreographer;
    private final Handler mHandler;
    private final Queue<Runnable> mQueue;

    private static final HashMap<Handler, ChoreographedExecutor> sExecutors = new HashMap<>();

    private ChoreographedExecutor(Handler handler) {
        mChoreographer = Choreographer.getInstance();
        mHandler = handler;
        mQueue = new LinkedList<>();
        assert mHandler.getLooper() == Looper.myLooper();
    }

    public static ChoreographedExecutor getInstance(Handler handler) {
        if (sExecutors.containsKey(handler)) {
            return sExecutors.get(handler);
        } else {
            ChoreographedExecutor executor = new ChoreographedExecutor(handler);
            sExecutors.put(handler, executor);
            return executor;
        }
    }

    private void enqueueNextTaskForHandler() {
        if (mQueue.isEmpty()) {
            return;
        }
        Runnable r = mQueue.poll();
        mHandler.post(r);
        if (!mQueue.isEmpty()) {
            postFrameCallback();
        }
    }

    private void postFrameCallback() {
        // The Choreographer will run the callback as part of the next frame render,
        // so we post the original task to the Handler inside the frame render callback.
        mChoreographer.postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                enqueueNextTaskForHandler();
            }
        });
    }

    @Override
    public void execute(Runnable r) {
        assert ThreadUtils.runningOnUiThread();
        mQueue.add(r);
        if (mQueue.size() == 1) {
            postFrameCallback();
        }
    }
}
