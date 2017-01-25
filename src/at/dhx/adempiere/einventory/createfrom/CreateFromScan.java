/******************************************************************************
 * Copyright (C) 2009 Low Heng Sin                                            *
 * Copyright (C) 2009 Idalica Corporation                                     *
 * Copyright (C) 2017 Daniel Haag                                             *
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;
import java.util.logging.Level;

import org.compiere.apps.IStatusBar;
import org.compiere.grid.CreateFrom;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;
import org.compiere.model.MBankAccount;
import org.compiere.model.MInventory;
import org.compiere.model.MInventoryLine;
import org.compiere.model.MLocator;
import org.compiere.model.MProduct;
import org.compiere.model.MRole;
import org.compiere.model.MWarehouse;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;

/**
 *  Create Inventory count with a scanner
 *
 *  Modified by dhx and based on CreateFromShipment from:
 *
 *  @author Jorg Janke
 *  @author Teo Sarca, SC ARHIPAC SERVICE SRL
 */
public abstract class CreateFromScan extends CreateFrom 
{
	private int defaultLocator_ID=0;

	/**
	 *  Protected Constructor
	 *  @param mTab MTab
	 */
	public CreateFromScan(GridTab mTab)
	{
		super(mTab);
		if (log.isLoggable(Level.INFO)) log.info(mTab.toString());
	}   //  VCreateFromShipment

	/**
	 *  Dynamic Init
	 *  @return true if initialized
	 */
	public boolean dynInit() throws Exception
	{
		log.config("");
		setTitle(Msg.getElement(Env.getCtx(), "M_InventoryLine_ID", false) + " .. " + Msg.translate(Env.getCtx(), "CreateFrom"));
		
		return true;
	}   //  dynInit


	/**
	 * Load Invoice details
	 * @param C_Invoice_ID Invoice
	 */
	protected Vector<Vector<Object>> getLocatorData(int M_Locator_ID, int M_Inventory_ID)
	{
		defaultLocator_ID = M_Locator_ID;
		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		StringBuilder sql = new StringBuilder("SELECT " // Entered UOM
				+ "l.QtyCount,"
				+ " l.M_Locator_ID, loc.Value, " // 2..3
				+ " l.M_Product_ID,p.Name, l.M_InventoryLine_ID, l.Line" // 4..6
				+ " FROM M_InventoryLine l ");

		sql.append(" LEFT OUTER JOIN M_Product p ON (l.M_Product_ID=p.M_Product_ID)")
		.append(" LEFT OUTER JOIN M_Locator loc on (l.M_Locator_ID=loc.M_Locator_ID)")
		.append(" WHERE l.M_Locator_ID=? AND l.M_Inventory_ID=? ")
		.append("ORDER BY l.Line");
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(1, M_Locator_ID);
			pstmt.setInt(2, M_Inventory_ID);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				Vector<Object> line = new Vector<Object>(7);
				line.add(new Boolean(false)); // 0-Selection
				line.add(rs.getBigDecimal(1)); // 1-QtyCount
				KeyNamePair pp = new KeyNamePair(rs.getInt(2), rs.getString(3).trim());
				line.add(pp); // 2-Locator
				pp = new KeyNamePair(rs.getInt(4), rs.getString(5));
				line.add(pp); // 3-Product
				pp = new KeyNamePair(rs.getInt(6), rs.getBigDecimal(7).toString());
				line.add(pp); // 4-Line
				data.add(line);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
			//throw new DBException(e, sql);
		}
	    finally
	    {
	    	DB.close(rs, pstmt);
	    	rs = null; pstmt = null;
	    }
		return data;
	}
	
	/**
	 * Get KeyNamePair for Locator.
	 * If no locator specified or the specified locator is not valid (e.g. warehouse not match),
	 * a default one will be used.
	 * @param M_Locator_ID
	 * @return KeyNamePair
	 */
	protected KeyNamePair getLocatorKeyNamePair(int M_Locator_ID)
	{
		MLocator locator = null;
		
		// Load desired Locator
		if (M_Locator_ID > 0)
		{
			locator = MLocator.get(Env.getCtx(), M_Locator_ID);
			// Validate warehouse
			if (locator != null && locator.getM_Warehouse_ID() != getM_Warehouse_ID())
			{
				locator = null;
			}
		}
		
		// Try to use default locator from Order Warehouse
		if (locator == null && p_order != null && p_order.getM_Warehouse_ID() == getM_Warehouse_ID())
		{
			MWarehouse wh = MWarehouse.get(Env.getCtx(), p_order.getM_Warehouse_ID());
			if (wh != null)
			{
				locator = wh.getDefaultLocator();
			}
		}
		// Try to get from locator field
		if (locator == null)
		{
			if (defaultLocator_ID > 0)
			{
				locator = MLocator.get(Env.getCtx(), defaultLocator_ID);
			}
		}
		// Validate Warehouse
		if (locator == null || locator.getM_Warehouse_ID() != getM_Warehouse_ID())
		{
			locator = MWarehouse.get(Env.getCtx(), getM_Warehouse_ID()).getDefaultLocator();
		}
		
		KeyNamePair pp = null ;
		if (locator != null)
		{
			pp = new KeyNamePair(locator.get_ID(), locator.getValue());
		}
		return pp;
	}
	
	protected int findLocatorId(String text)
	{
		if (text == null || text.length() == 0)
		{
			return 0;
		}
		
		if (text.endsWith("%"))
			text = text.toUpperCase();
		else
			text = text.toUpperCase() + "%";
		
		int M_Locator_ID = new Query(Env.getCtx(),
				MLocator.Table_Name,
				"UPPER(Value) LIKE ?",
				null)
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setParameters(new Object[] { text })
				.firstId();
				
		return M_Locator_ID;
	}
	
	/**
	 *  List number of rows selected
	 */
	public void info(IMiniTable miniTable, IStatusBar statusBar)
	{

	}

	protected void configureMiniTable (IMiniTable miniTable)
	{
		miniTable.setColumnClass(0, Boolean.class, false);     //  Selection
		miniTable.setColumnClass(1, BigDecimal.class, false);      //  QtyCount
		miniTable.setColumnClass(2, String.class, true);          //  Locator
		miniTable.setColumnClass(3, String.class, false);  //  Product
		miniTable.setColumnClass(4, String.class, true);   //  Line
		
		//  Table UI
		miniTable.autoSize();
		
	}

	/**
	 *  Save - Create Invoice Lines
	 *  @return true if saved
	 */
	public boolean save(IMiniTable miniTable, String trxName)
	{
		int M_Locator_ID = defaultLocator_ID;
		if (M_Locator_ID == 0) {
			return false;
		}
		// Get Shipment
		int M_Inventory_ID = ((Integer) getGridTab().getValue("M_Inventory_ID")).intValue();
		MInventory inventory = new MInventory(Env.getCtx(), M_Inventory_ID, trxName);
		if (log.isLoggable(Level.CONFIG)) log.config(inventory + ", C_Locator_ID=" + M_Locator_ID);

		// Lines
		for (int i = 0; i < miniTable.getRowCount(); i++)
		{
			if (((Boolean)miniTable.getValueAt(i, 0)).booleanValue()) {
				// variable values
				BigDecimal QtyCount = (BigDecimal) miniTable.getValueAt(i, 1); // Qty
				KeyNamePair pp = (KeyNamePair) miniTable.getValueAt(i, 2); // Locator
				M_Locator_ID = pp!=null && pp.getKey()!=0 ? pp.getKey() : defaultLocator_ID;

				pp = (KeyNamePair) miniTable.getValueAt(i, 3); // Product
				int M_Product_ID = pp.getKey();
				int precision = 2;
				if (M_Product_ID != 0)
				{
					MProduct product = MProduct.get(Env.getCtx(), M_Product_ID);
					precision = product.getUOMPrecision();
				}
				QtyCount = QtyCount.setScale(precision, BigDecimal.ROUND_HALF_DOWN);
				//
				if (log.isLoggable(Level.FINE)) log.fine("Line QtyCount=" + QtyCount
						+ ", Product=" + M_Product_ID);

				pp = (KeyNamePair) miniTable.getValueAt(i, 4); // Line
				int M_InventoryLine_ID = pp.getKey();

				//	Create new Inventory Line
				MInventoryLine il = new MInventoryLine(Env.getCtx(), M_InventoryLine_ID, trxName);
				il.setM_Product_ID(M_Product_ID);
				il.setQtyCount(QtyCount);
				il.setM_Locator_ID(M_Locator_ID);
				il.saveEx();
			}   //   if selected
		}   //  for all rows


		return true;		

	}   //  saveInvoice

	protected Vector<String> getOISColumnNames()
	{
		//  Header Info
	    Vector<String> columnNames = new Vector<String>(7);
	    columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
	    columnNames.add(Msg.translate(Env.getCtx(), "Quantity"));
	    columnNames.add(Msg.translate(Env.getCtx(), "M_Locator_ID"));
	    columnNames.add(Msg.translate(Env.getCtx(), "M_Product_ID"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "M_InvoiceLine_ID", false));
	    
	    return columnNames;
	}


}
