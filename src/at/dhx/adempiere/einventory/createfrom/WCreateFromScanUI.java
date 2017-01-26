/******************************************************************************
 * Copyright (C) 2009 Low Heng Sin                                            *
 * Copyright (C) 2009 Idalica Corporation                                     *
 * Copyright (C) 2017 Daniel Haag
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/

package at.dhx.adempiere.einventory.createfrom;

import java.math.BigDecimal;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.apps.form.WCreateFromWindow;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListModelTable;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.editor.WLocatorEditor;
import org.adempiere.webui.editor.WStringEditor;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.compiere.grid.CreateFromShipment;
import org.compiere.model.GridTab;
import org.compiere.model.MInventory;
import org.compiere.model.MInventoryLine;
import org.compiere.model.MLocator;
import org.compiere.model.MLocatorLookup;
import org.compiere.model.MProduct;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Space;
import org.zkoss.zul.Vlayout;

public class WCreateFromScanUI extends CreateFromScan implements EventListener<Event>, ValueChangeListener
{

	private WCreateFromWindow window;
	
	public WCreateFromScanUI(GridTab tab) 
	{
		super(tab);
		log.info(getGridTab().toString());
		
		window = new WCreateFromWindow(this, getGridTab().getWindowNo());
		
		p_WindowNo = getGridTab().getWindowNo();

		try
		{
			if (!dynInit())
				return;
			zkInit();
			setInitOK(true);
		}
		catch(Exception e)
		{
			log.log(Level.SEVERE, "", e);
			setInitOK(false);
			throw new AdempiereException(e.getMessage());
		}
		AEnv.showWindow(window);
	}
	
	/** Window No               */
	private int p_WindowNo;

	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(getClass());
		
	protected Label locatorLabel = new Label();
	protected WLocatorEditor locatorField = new WLocatorEditor();
	protected Label scanLabel = new Label();
	protected WStringEditor scanField = new WStringEditor();
	protected int inventoryId = 0;
	protected MInventory inventory = null;
    
	/**
	 *  Dynamic Init
	 *  @throws Exception if Lookups cannot be initialized
	 *  @return true if initialized
	 */
	public boolean dynInit() throws Exception
	{
		log.config("");
		
		super.dynInit();
		
		window.setTitle(getTitle());

		//  load Locator
		MLocatorLookup locator = new MLocatorLookup(Env.getCtx(), p_WindowNo);
		locatorField = new WLocatorEditor ("M_Locator_ID", true, false, true, locator, p_WindowNo);
		locatorField.getComponent().addEventListener(Events.ON_CHANGE, this);
		locatorLabel.setMandatory(true);

		scanField = new WStringEditor ("UPC", false, false, true, 10, 30, null, null);
		scanField.getComponent().addEventListener(Events.ON_CHANGE, this);

		inventoryId = Env.getContextAsInt(Env.getCtx(), p_WindowNo, "M_Inventory_ID");
		inventory = new MInventory(Env.getCtx(), inventoryId, null);
		
		return true;
	}   //  dynInit
	
	protected void zkInit() throws Exception
	{
    	locatorLabel.setText(Msg.translate(Env.getCtx(), "M_Locator_ID"));
        scanLabel.setText(Msg.getElement(Env.getCtx(), "UPC", false));

		Vlayout vlayout = new Vlayout();
		vlayout.setVflex("1");
		vlayout.setWidth("100%");
    	Panel parameterPanel = window.getParameterPanel();
		parameterPanel.appendChild(vlayout);
		
		Grid parameterStdLayout = GridFactory.newGridLayout();
    	vlayout.appendChild(parameterStdLayout);
    	
		Rows rows = (Rows) parameterStdLayout.newRows();
		Row row = rows.newRow();

		row.appendChild(scanLabel.rightAlign());
		row.appendChild(scanField.getComponent());

		//row.appendChild(new Space());

		row.appendChild(locatorLabel.rightAlign());
		row.appendChild(locatorField.getComponent());
	}

	private boolean 	m_actionActive = false;
	
	/**
	 *  Action Listener
	 *  @param e event
	 * @throws Exception 
	 */
	public void onEvent(Event e) throws Exception
	{
		if (m_actionActive)
			return;
		m_actionActive = true;

		if (e.getTarget().equals(scanField.getComponent()))
		{
			String scan = scanField.getDisplay();
			List<MProduct> products = MProduct.getByUPC(Env.getCtx(), scan, null);
			if (products.size() < 1) {
				int M_Locator_ID = findLocatorId(scan);
				if (M_Locator_ID > -1) {
					String cdisplay = locatorField.getDisplay();
					Object cvalue = locatorField.getValue();
					if ( ! cvalue.equals(M_Locator_ID) ) {
						locatorField.setValue(M_Locator_ID);						
						loadLocator(inventoryId, locatorField.getM_Locator_ID());
					}
				}
			} else {
				checkProductUsingUPC();				
			}
			scanField.setValue("");
		}
		m_actionActive = false;
	}
	
	/**
	 * Checks the UPC value and checks if the UPC matches any of the products in the
	 * list.
	 */
	private void checkProductUsingUPC()
	{
		String upc = scanField.getDisplay();
		//DefaultTableModel model = (DefaultTableModel) dialog.getMiniTable().getModel();
		ListModelTable model = (ListModelTable) window.getWListbox().getModel();
		
		// Lookup UPC
		List<MProduct> products = MProduct.getByUPC(Env.getCtx(), upc, null);
		if (products.size() > 1) {
			log.warning("Multiple Products with the same UPC found: " + upc);
			return;
		}
		for (MProduct product : products)
		{
			int row = findProductRow(product.get_ID());
			if (row >= 0)
			{
				KeyNamePair il = (KeyNamePair)model.getValueAt(row, 4);
				MInventoryLine iline = new MInventoryLine(Env.getCtx(), il.getKey(), null);
				BigDecimal qty = iline.getQtyCount().add(BigDecimal.ONE);
				iline.setQtyCount(qty);
				if (iline.save()) {
					model.setValueAt(qty, row, 1);
					model.setValueAt(Boolean.TRUE, row, 0);
					model.updateComponent(row, row);
				}
			} else {
				// no existing inventory line for this product at this locator
				// create a new line
				MInventoryLine nline = new MInventoryLine(inventory,
						locatorField.getM_Locator_ID(),
						product.getM_Product_ID(),
						0, BigDecimal.ZERO, BigDecimal.ONE);
				
				if (nline.save()) {
					Vector<Object> line = new Vector<Object>(7);
					line.add(new Boolean(true)); // 0-Selection
					line.add(BigDecimal.ONE); // 1-QtyCount
					KeyNamePair pp = new KeyNamePair(locatorField.getM_Locator_ID(), locatorField.getDisplay());
					line.add(pp); // 2-Locator
					pp = new KeyNamePair(product.getM_Product_ID(), product.getName());
					line.add(pp); // 3-Product
					pp = new KeyNamePair(nline.getM_InventoryLine_ID(), String.valueOf(nline.getLine()));
					line.add(pp); // 4-Line
					model.add(line);
					model.updateComponent(model.getRowCount(), model.getRowCount());
				}
				
			}
		}
	}

	/**
	 * Finds the row where a given product is. If the product is not found
	 * in the table -1 is returned.
	 * @param M_Product_ID
	 * @return  Row of the product or -1 if non existing.
	 * 
	 */
	private int findProductRow(int M_Product_ID)
	{
		//DefaultTableModel model = (DefaultTableModel)dialog.getMiniTable().getModel();
		ListModelTable model = (ListModelTable) window.getWListbox().getModel();
		KeyNamePair kp;
		for (int i=0; i<model.getRowCount(); i++) {
			kp = (KeyNamePair)model.getValueAt(i, 3);
			if (kp.getKey()==M_Product_ID) {
				return(i);
			}
		}
		return(-1);
	}
		
	/**
	 *  Change Listener
	 *  @param e event
	 */
	public void valueChange (ValueChangeEvent e)
	{
		if (log.isLoggable(Level.CONFIG)) log.config(e.getPropertyName() + "=" + e.getNewValue());
		window.tableChanged(null);
	}   //  vetoableChange
	
	/**
	 *  Load Data - Locator
	 *  @param M_Locator_ID
	 */
	protected void loadLocator (int M_Inventory_ID, int M_Locator_ID)
	{
		loadTableOIS(getLocatorData(M_Locator_ID, M_Inventory_ID));
	}
		
	/**
	 *  Load Order/Invoice/Shipment data into Table
	 *  @param data data
	 */
	protected void loadTableOIS (Vector<?> data)
	{
		window.getWListbox().clear();
		
		//  Remove previous listeners
		window.getWListbox().getModel().removeTableModelListener(window);
		//  Set Model
		ListModelTable model = new ListModelTable(data);
		model.addTableModelListener(window);
		window.getWListbox().setData(model, getOISColumnNames());
		//
		
		configureMiniTable(window.getWListbox());
	}   //  loadOrder
	
	public void showWindow()
	{
		window.setVisible(true);
	}
	
	public void closeWindow()
	{
		window.dispose();
	}

	@Override
	public Object getWindow() {
		return window;
	}
}
