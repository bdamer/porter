package com.afqa123.porter;


public abstract class StoppableThread extends Thread {

	private volatile boolean _stop = false;
	
	protected boolean isStopped() {
		return _stop;
	}
	
	public synchronized void requestStop() {
		_stop = true;
	}
}
