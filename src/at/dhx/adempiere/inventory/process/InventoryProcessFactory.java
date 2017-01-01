package at.dhx.adempiere.inventory.process;

import org.adempiere.base.IProcessFactory;
import org.compiere.process.ProcessCall;

public class InventoryProcessFactory implements IProcessFactory {

	@Override
	public ProcessCall newProcessInstance(String className) {
		if (className.equals(InventoryBookingCountUpdate.class.getName())) {
			return new InventoryBookingCountUpdate();
		}
		return null;
	}

}
