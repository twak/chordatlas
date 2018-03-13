package org.twak.tweed.gen;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.swing.JComponent;

import org.twak.tweed.Tweed;
import org.twak.utils.ui.Rainbow;

import com.jme3.scene.Node;

public abstract class Gen {

	public String name;
	public boolean visible = true;
	public Color color = Rainbow.next(Object.class);

	public transient Node gNode;
	public transient Tweed tweed;
	
	protected transient Node parentNode;
	
	public Gen() {}
	
	public Gen(String name, Tweed tweed) {
		this.name = name;
		init(tweed);
	}

	private void init(Tweed tweed) {
		this.tweed = tweed;
		parentNode = tweed.getRootNode();
		gNode = new Node("gen " + name);
	}
	
	public void onLoad( Tweed tweed ) {
		init (tweed);
	}

	public void calculate() {
		setVisible(visible);
	}
	
	public final void calculateOnJmeThread() {
		tweed.enqueue( new Runnable() {
			@Override
			public void run() {
				calculate();
			}
		} );
	}
	
	public void setVisible(boolean visible) {
		this.visible = visible;
		
		tweed.frame.refreshGenList();
		
		tweed.enqueue(new Callable() {
			public Object call() throws Exception {
				if (visible) 
					parentNode.attachChild(gNode);
				else
					gNode.removeFromParent();

				tweed.getRootNode().updateGeometricState();
				tweed.gainFocus();
				
				return null;
			}
		});
	}

	public abstract JComponent getUI();

	public void toggleVis() {
		setVisible(!visible);
	}

	public void setColor(Color color2) {
		this.color = color2;

		tweed.enqueue(new Callable() {
			public Object call() throws Exception {
				calculate();
				return null;
			}
		});
	}

	public void kill() {
	}
}
