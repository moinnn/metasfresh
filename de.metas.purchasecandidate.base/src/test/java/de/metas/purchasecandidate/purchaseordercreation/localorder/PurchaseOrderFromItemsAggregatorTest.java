package de.metas.purchasecandidate.purchaseordercreation.localorder;

import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.newInstanceOutOfTrx;
import static org.adempiere.model.InterfaceWrapperHelper.save;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.adempiere.ad.trx.api.ITrxManager;
import org.adempiere.mm.attributes.AttributeSetInstanceId;
import org.adempiere.service.OrgId;
import org.adempiere.test.AdempiereTestHelper;
import org.adempiere.util.Services;
import org.adempiere.util.time.SystemTime;
import org.adempiere.warehouse.WarehouseId;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_UOM;
import org.compiere.model.X_C_Order;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import de.metas.ShutdownListener;
import de.metas.StartupListener;
import de.metas.bpartner.BPartnerId;
import de.metas.money.grossprofit.GrossProfitPriceFactory;
import de.metas.order.OrderAndLineId;
import de.metas.pricing.conditions.PricingConditions;
import de.metas.product.ProductAndCategoryId;
import de.metas.purchasecandidate.DemandGroupReference;
import de.metas.purchasecandidate.PurchaseCandidate;
import de.metas.purchasecandidate.PurchaseCandidateTestTool;
import de.metas.purchasecandidate.VendorProductInfo;
import de.metas.purchasecandidate.purchaseordercreation.remoteorder.NullVendorGatewayInvoker;
import de.metas.purchasecandidate.purchaseordercreation.remotepurchaseitem.PurchaseOrderItem;
import de.metas.quantity.Quantity;

/*
 * #%L
 * de.metas.purchasecandidate.base
 * %%
 * Copyright (C) 2018 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { StartupListener.class, ShutdownListener.class, GrossProfitPriceFactory.class })
public class PurchaseOrderFromItemsAggregatorTest
{
	private I_C_UOM EACH;
	private Quantity TEN;

	@Before
	public void init()
	{
		AdempiereTestHelper.get().init();

		this.EACH = createUOM("Ea");
		this.TEN = Quantity.of(BigDecimal.TEN, EACH);
	}

	private I_C_UOM createUOM(final String name)
	{
		final I_C_UOM uom = newInstanceOutOfTrx(I_C_UOM.class);
		uom.setName(name);
		uom.setUOMSymbol(name);
		save(uom);
		return uom;
	}

	@Test
	public void test()
	{

		// will be needed for user notification (CreatedBy)
		final I_C_Order salesOrder = newInstance(I_C_Order.class);
		save(salesOrder);

		// needed to construct the user notification message
		final I_C_BPartner vendor = newInstance(I_C_BPartner.class);
		vendor.setValue("Vendor");
		vendor.setName("Vendor");
		save(vendor);

		final ProductAndCategoryId productAndCategoryId = ProductAndCategoryId.of(20, 30);

		final VendorProductInfo vendorProductInfo = VendorProductInfo.builder()
				.productAndCategoryId(productAndCategoryId)
				.attributeSetInstanceId(AttributeSetInstanceId.ofRepoId(40))
				.vendorId(BPartnerId.ofRepoId(vendor.getC_BPartner_ID()))
				.defaultVendor(false)
				.vendorProductNo("productNo")
				.vendorProductName("productName")
				.pricingConditions(PricingConditions.builder().build())
				.build();

		final PurchaseCandidate purchaseCandidate = PurchaseCandidate.builder()
				.groupReference(DemandGroupReference.createEmpty())
				.orgId(OrgId.ofRepoId(10))
				.purchaseDatePromised(SystemTime.asLocalDateTime())
				.vendorId(vendorProductInfo.getVendorId())
				.aggregatePOs(vendorProductInfo.isAggregatePOs())
				.productId(vendorProductInfo.getProductId())
				.attributeSetInstanceId(vendorProductInfo.getAttributeSetInstanceId())
				.vendorProductNo(vendorProductInfo.getVendorProductNo())
				.qtyToPurchase(TEN)
				.salesOrderAndLineIdOrNull(OrderAndLineId.ofRepoIds(salesOrder.getC_Order_ID(), 50))
				.warehouseId(WarehouseId.ofRepoId(60))
				.profitInfo(PurchaseCandidateTestTool.createPurchaseProfitInfo())
				.build();

		final PurchaseOrderFromItemsAggregator aggregator = PurchaseOrderFromItemsAggregator.newInstance();

		Services.get(ITrxManager.class).run(() -> {
			aggregator.add(PurchaseOrderItem.builder()
					.purchaseCandidate(purchaseCandidate)
					.datePromised(SystemTime.asLocalDateTime())
					.purchasedQty(TEN)
					.remotePurchaseOrderId(NullVendorGatewayInvoker.NO_REMOTE_PURCHASE_ID)
					.build());

			aggregator.closeAllGroups();
		});

		final List<I_C_Order> createdPurchaseOrders = aggregator.getCreatedPurchaseOrders();
		assertThat(createdPurchaseOrders).hasSize(1);

		final I_C_Order purchaseOrder = createdPurchaseOrders.get(0);
		assertThat(purchaseOrder.getDocStatus()).isEqualTo(X_C_Order.DOCSTATUS_Completed);

		// these properties are currently set by MOrder.beforeSafe, which is not called in our test
		// assertThat(purchaseOrder.getM_PricingSystem_ID()).isGreaterThan(0);
		// assertThat(purchaseOrder.getM_PriceList_ID()).isGreaterThan(0);
		// assertThat(purchaseOrder.getC_Currency_ID()).isGreaterThan(0);
	}
}
