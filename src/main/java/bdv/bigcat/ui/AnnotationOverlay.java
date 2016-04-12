package bdv.bigcat.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.List;

import bdv.bigcat.annotation.Annotation;
import bdv.bigcat.annotation.AnnotationController;
import bdv.bigcat.annotation.AnnotationVisitor;
import bdv.bigcat.annotation.Annotations;
import bdv.bigcat.annotation.Synapse;
import bdv.bigcat.annotation.SynapticSite;
import bdv.util.Affine3DHelpers;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.kdtree.ConvexPolytope;
import net.imglib2.algorithm.kdtree.HyperPlane;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;

/**
 * @author Jan Funke <jfunke@iri.upc.edu>
 *
 */
public class AnnotationOverlay implements OverlayRenderer
{
	public AnnotationOverlay( final ViewerPanel viewer, final Annotations annotations, final AnnotationController controller )
	{
		this.viewer = viewer;
		this.annotations = annotations;
		this.controller = controller;
	}
	
	public void setVisible( final boolean visible )
	{
		this.visible = visible;
	}
	
	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	@Override
	public void drawOverlays( Graphics g )
	{
		if ( visible )
		{
			final double scale;
			synchronized ( viewer )
			{
				viewer.getState().getViewerTransform( viewerTransform );
				scale = Affine3DHelpers.extractScale( viewerTransform, 0 );
			}
			
			AffineTransform3D invTransform = viewerTransform.inverse();
			
			HyperPlane left   = new HyperPlane(1, 0, 0, 0);
			HyperPlane right  = new HyperPlane(-1, 0, 0, -width);
			HyperPlane bottom = new HyperPlane(0, 1, 0, 0);
			HyperPlane top    = new HyperPlane(0, -1, 0, -height);
			HyperPlane front  = new HyperPlane(0, 0, -1, -visibilityThreshold);
			HyperPlane back   = new HyperPlane(0, 0, 1, -visibilityThreshold);
			
			ConvexPolytope visibilityClip = ConvexPolytope.transform(new ConvexPolytope(left, right, bottom, top, front, back), invTransform);
			
			List< Annotation > visibleAnnotations = annotations.getLocalAnnotations(visibilityClip);

			Graphics2D g2d = (Graphics2D)g;
			class AnnotationRenderer extends AnnotationVisitor {
				
				private final int pass;
				
				AnnotationRenderer(int pass) {
					this.pass = pass;
				}
				
				@Override
				public void visit(Synapse s) {
					
					RealPoint displayPosition = new RealPoint(3);
					viewerTransform.apply(s.getPosition(), displayPosition);

					float zAlpha = Math.max(0, (float)1.0 - (float)0.1*Math.abs(displayPosition.getFloatPosition(2)));
					g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, zAlpha));
					
					if (pass == 0) {

						g2d.setPaint(synapticSiteColor.brighter());
						g2d.setStroke(new BasicStroke(4.0f));
						for (SynapticSite site : s.getPostSynapticPartners()) {
							
							RealPoint siteDisplayPosition = new RealPoint(3);
							viewerTransform.apply(site.getPosition(), siteDisplayPosition);
							
							g2d.draw(new Line2D.Double(
									displayPosition.getDoublePosition(0),
									displayPosition.getDoublePosition(1),
									siteDisplayPosition.getDoublePosition(0),
									siteDisplayPosition.getDoublePosition(1)
									));
						}
					}

					if (pass == 1) {
						final int radius = 10;
						if (s == controller.getSelectedAnnotation())
							g2d.setPaint(selectedSynapseColor);
						else
							g2d.setPaint(synapseColor);
						g2d.fillOval(
								Math.round(displayPosition.getFloatPosition(0) - radius),
								Math.round(displayPosition.getFloatPosition(1) - radius),
								2 * radius + 1,
								2 * radius + 1 );
						g2d.setPaint(synapseColor.darker());
						g2d.drawOval(
								Math.round(displayPosition.getFloatPosition(0) - radius),
								Math.round(displayPosition.getFloatPosition(1) - radius),
								2 * radius + 1,
								2 * radius + 1 );
					}
				}

				@Override
				public void visit(SynapticSite synapticSite) {
					
					if (pass != 1)
						return;
					
					RealPoint displayPosition = new RealPoint(3);
					viewerTransform.apply(synapticSite.getPosition(), displayPosition);

					float zAlpha = Math.max(0, (float)1.0 - (float)0.1*Math.abs(displayPosition.getFloatPosition(2)));
					g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, zAlpha));

					final int radius = 10;
					g2d.setPaint(synapticSiteColor);
					g2d.fillOval(
							Math.round(displayPosition.getFloatPosition(0) - radius),
							Math.round(displayPosition.getFloatPosition(1) - radius),
							2 * radius + 1,
							2 * radius + 1 );
					g2d.setPaint(synapticSiteColor.darker());
					g2d.drawOval(
							Math.round(displayPosition.getFloatPosition(0) - radius),
							Math.round(displayPosition.getFloatPosition(1) - radius),
							2 * radius + 1,
							2 * radius + 1 );
				}
			}
			
			AnnotationRenderer renderer = new AnnotationRenderer(0);
			for (Annotation a : visibleAnnotations)
				a.accept(renderer);
			renderer = new AnnotationRenderer(1);
			for (Annotation a : visibleAnnotations)
				a.accept(renderer);
		}
	}

	@Override
	public void setCanvasSize( int width, int height )
	{
		this.width = width;
		this.height = height;
	}

	final protected ViewerPanel viewer;
	final private Annotations annotations;
	final private AnnotationController controller;
	protected boolean visible = false;
	final AffineTransform3D viewerTransform = new AffineTransform3D();
	private int width, height;
	final private int visibilityThreshold = 10; // show annotations closer than this to currently visible plane
	
	final static private Color synapseColor = new Color(155, 13, 75);
	final static private Color selectedSynapseColor = new Color(155, 155, 75);
	final static private Color synapticSiteColor = new Color(75, 13, 155);
}
