package org.twak.viewTrace.franken.style.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.tweed.TweedSettings;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.WindowManager;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.Pix2Pix;
import org.twak.viewTrace.franken.style.MultiModal;
import org.twak.viewTrace.franken.style.MultiModal.Mode;

public class MultiModalEditor extends JPanel {

	NetExamples egs;
	private MultiModal mm;
	NetInfo exemplar;
	JFrame frame;
	JPanel gList;
	Runnable globalUpdate ;
	
	public MultiModalEditor( MultiModal mm, NetInfo exemplar, Runnable globalUpdate ) {
		
		this.mm = mm;
		this.exemplar = exemplar;
		this.globalUpdate = globalUpdate;
		
		
		setLayout( new BorderLayout() );
		egs = new NetExamples( mm, 8, 8, exemplar, new File ( TweedSettings.settings.egNetworkInputs + "/textureatlas/" + exemplar.name) );
		
		JPanel controls = createControls(() -> egs.changed());

		
		add(controls, BorderLayout.EAST);
		add(egs, BorderLayout.CENTER);
	}
	
	public void openFrame() {
		
		JPanel panel = new JPanel(new BorderLayout() );
		panel.add(this, BorderLayout.CENTER);
		
		JButton close = new JButton( "ok" );
		close.addActionListener( l -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)) );
		panel.add( close, BorderLayout.SOUTH );
		
		frame = WindowManager.frame( "multi-modal editor",  panel);
		
		frame.addWindowListener( new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				stop();
				globalUpdate.run();
			};
		} );
		
		if (mm.modes.isEmpty())
			addG( () -> egs.changed(), null );
		
		
		
		frame.pack();
		frame.setVisible( true );
	}
	
	public void stop() {
		egs.stop();
	}

	private JPanel createControls( Runnable localUpdate ) {
		
		gList = new JPanel(new ListDownLayout());
		
		gList.setPreferredSize( new Dimension (200, 600) );
		
		buildControls(gList, localUpdate);
		JScrollPane scroll = new JScrollPane( gList );
		
		JPanel out = new JPanel();
		out.setLayout( new BorderLayout() );
		out.add (scroll, BorderLayout.CENTER);
		
		JPanel northPanel = new JPanel(new BorderLayout());
		JButton add = new JButton ("+");
		add.addActionListener( e -> addG( localUpdate, null ) );
		northPanel.add( add, BorderLayout.EAST );
		northPanel.add( new JLabel("modes:"), BorderLayout.WEST );
		
		add.setDropTarget(new DropTarget() {
		    public synchronized void drop(DropTargetDropEvent evt) {
		        try {
		            evt.acceptDrop(DnDConstants.ACTION_COPY);
		            List<File> droppedFiles = (List<File>)
		                evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
		            new Thread() {
		            	public void run() {
	            			addG( localUpdate, droppedFiles.get( 0 ) );
		            	};
		            }.start();
		        } catch (Exception ex) {
		            ex.printStackTrace();
		        }
		    }
		});
		
		
		
		out.add( northPanel, BorderLayout.NORTH );

		
		return out;
	}
	
	public void addG(Runnable localUpdate, File meanImage ) {
		
		Mode w = mm.newMode();

		if (meanImage == null) {
			GaussWrapper c = new GaussWrapper( w, localUpdate );
			gList.add( c );
			gList.revalidate();
		}
		else {
			new Pix2Pix( exemplar ).encode( meanImage, w.ss.mean, new Runnable() {
				@Override
				public void run() {
					GaussWrapper c = new GaussWrapper( w, localUpdate );
					gList.add( c );
					gList.revalidate();
				}
			} );
		}
		
	}
	
	public void buildControls(JPanel gauss, Runnable localUpdate) {
		for (Mode gsw : mm.modes) {
			gauss.add (new GaussWrapper( gsw, localUpdate ));
		}
	}
	
	public final static Border BORDER = BorderFactory.createCompoundBorder( 
			BorderFactory.createEmptyBorder( 4, 4, 4, 4 ), 
			BorderFactory.createCompoundBorder( BorderFactory.createEtchedBorder(EtchedBorder.RAISED),
					BorderFactory.createEmptyBorder( 2, 2, 2, 2 ) ) );
	
	private class GaussWrapper extends JPanel {
		
		public GaussWrapper (Mode w, Runnable localUpdate ) {
			
			setLayout(  new BorderLayout() );
			setBorder( BORDER );
			
//			setBorder( new LineBorder( Color.darkGray, 2, true ) );
			setOpaque( true );
			setBackground( Color.white );
			
			JPanel closerP = new JPanel( new BorderLayout() );
			JButton close  = new JButton ("x");
			
			JLabel pLabel = new JLabel("p:");
			
			
			JSlider pSlider = new JSlider (0, 1000, (int) ( w.prob * 1000 ) );
			pSlider.addChangeListener( new ChangeListener() {
				@Override
				public void stateChanged( ChangeEvent e ) {
					if (!pSlider.getValueIsAdjusting()) {
						w.prob = pSlider.getValue() / 1000.;
						mm.updateModes();
						localUpdate.run();
					}
				}
			} );
			
			closerP.add( pLabel, BorderLayout.WEST );
			closerP.add( pSlider, BorderLayout.CENTER );
			closerP.add( close, BorderLayout.EAST );
			
			
			close.addActionListener( new ActionListener() {
				
				@Override
				public void actionPerformed( ActionEvent e ) {
					mm.modes.remove( w );
					GaussWrapper.this.getParent().remove (GaussWrapper.this);
					new Thread () {
						public void run() {
							try {
								SwingUtilities.invokeAndWait( ( ) -> gList.revalidate() );
								gList.repaint();
							} catch ( Throwable e ) {
								e.printStackTrace();
							}
						}
					}.start();
					
					mm.updateModes();
					localUpdate.run();
				}
			} );
			
			add (closerP, BorderLayout.NORTH);
			add (w.ss.getUI( localUpdate ), BorderLayout.CENTER);
		}
		
	}
	

}
