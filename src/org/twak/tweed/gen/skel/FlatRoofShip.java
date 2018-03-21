package org.twak.tweed.gen.skel;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.twak.camp.Corner;
import org.twak.camp.Edge;
import org.twak.camp.Skeleton.HeresTheArea;
import org.twak.camp.SkeletonCapUpdate;
import org.twak.camp.debug.DebugDevice;
import org.twak.camp.ui.Marker;
import org.twak.siteplan.anchors.Anchor;
import org.twak.siteplan.anchors.AnchorHauler.AnchorHeightEvent;
import org.twak.siteplan.anchors.CapShip;
import org.twak.siteplan.anchors.Ship;
import org.twak.siteplan.campskeleton.Plan;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.siteplan.campskeleton.Siteplan;
import org.twak.utils.LContext;
import org.twak.utils.WeakListener;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.collections.DHash;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.SetCorrespondence;

public class FlatRoofShip extends Ship
{
    transient boolean addCap = true; // really for debug no need to serialize yet
    boolean atZeroHeight = false;

    double cap;
    Plan plan;
    
    public FlatRoofShip() {}
    
    public FlatRoofShip(double cap, Plan plan) {
    	this.cap = cap;
    	this.plan = plan;
    }
    
    
    @Override
    public JComponent getToolInterface( WeakListener refreshAnchors, Changed refreshFeatureListListener, Plan plan )
    {
        return new JPanel();
    }

    @Override
    protected Instance createInstance()
    {
        return new FlatRoofInstance();
    }

    public class FlatRoofInstance extends Instance
    {
        @Override
        public LContext<Corner> process( Anchor anchor, LContext<Corner> toEdit, Marker planMarker, Marker profileMarker, Edge edge, AnchorHeightEvent hauler, Corner oldLeadingCorner )
        {
        	 PlanSkeleton skel = hauler.skel;
        	 SkeletonCapUpdate capUpdate = new SkeletonCapUpdate(skel);
             
             LoopL<Corner> flatTop = capUpdate.getCap(cap);
             
             capUpdate.update(new LoopL<>(), new SetCorrespondence<Corner, Corner>(), new DHash<Corner, Corner>());
             
             LoopL<Point3d> togo =
                     flatTop.new Map<Point3d>()
                     {
                         @Override
                         public Point3d map( Loopable<Corner> input )
                         {
                             return new Point3d( input.get().x, input.get().y, input.get().z );
                         }
                     }.run();
                     skel.output.addNonSkeletonOutputFace( togo, new Vector3d( 0, 0, 1 ) );

			skel.capArea = Loopz.area3( togo );
                     
             DebugDevice.dump("post cap dump", skel);

             skel.qu.clearFaceEvents();
             skel.qu.clearOtherEvents();
            
            return null;
        }
    }

    @Override
    protected Anchor createNewAnchor()
    {
        return plan.createAnchor( Plan.AnchorType.PROFILE, this );
    }

    @Override
    public String getFeatureName()
    {
        return ("cap-roof");
    }

    @Override
    public Ship clone( Plan plan )
    {
        return new CapShip();
    }

}

