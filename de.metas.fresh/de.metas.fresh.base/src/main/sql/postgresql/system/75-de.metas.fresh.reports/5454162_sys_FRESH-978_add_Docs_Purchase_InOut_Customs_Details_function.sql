DROP FUNCTION IF EXISTS de_metas_endcustomer_fresh_reports.Docs_Purchase_InOut_Customs_Details(IN c_order_id numeric);

CREATE OR REPLACE FUNCTION de_metas_endcustomer_fresh_reports.Docs_Purchase_InOut_Customs_Details(IN c_order_id numeric)
RETURNS TABLE 
	(
	name character varying(255),
	value character varying(100),
	qty numeric,
	uomsymbol character varying(10),
	stdprecision numeric
	)
AS
$$	
SELECT
	p.Name, --Product
	thu.value,
	thu.qty AS qty,
	uom.UOMSymbol,
	uom.StdPrecision
FROM
	-- All In Outs linked to the order
	(
		SELECT DISTINCT
			iol.M_InOut_ID, ol.C_Order_ID
		FROM
			C_OrderLine ol
			INNER JOIN M_ReceiptSchedule rs ON rs.AD_Table_ID = ( SELECT Get_Table_ID('C_OrderLine') ) AND rs.Record_ID = ol.C_OrderLine_ID
			INNER JOIN M_ReceiptSchedule_Alloc rsa ON rs.M_ReceiptSchedule_ID = rsa.M_ReceiptSchedule_ID
			INNER JOIN M_InOutLine iol ON iol.M_InOutLine_ID = rsa.M_InOutLine_ID
		WHERE
			ol.C_Order_ID = $1
	) io
	/*
	 * Now, join all in out lines of those in outs. Might be more than the in out lines selected in the previous
	 * sub select because not all in out lines are linked to the order (e.g Packing material). NOTE: Due to the
	 * process we assume, that all lines of one inout belong to only one order
	 */
	INNER JOIN M_InOutLine iol ON io.M_InOut_ID = iol.M_InOut_ID
	INNER JOIN M_Product p ON iol.M_Product_ID = p.M_Product_ID
	LEFT OUTER JOIN (
		SELECT
			thuas.Record_ID, thu.value,
			1000000 AS C_UOM_ID, -- kilogramm
			COALESCE ( thuwns.valuenumber, thuwn.valuenumber ) AS qty
		FROM
			C_OrderLine ol
			INNER JOIN M_ReceiptSchedule rs ON rs.AD_Table_ID = ( SELECT Get_Table_ID('C_OrderLine') ) AND rs.Record_ID = ol.C_OrderLine_ID
			INNER JOIN M_ReceiptSchedule_Alloc rsa ON rs.M_ReceiptSchedule_ID = rsa.M_ReceiptSchedule_ID
			INNER JOIN M_InOutLine iol ON iol.M_InOutLine_ID = rsa.M_InOutLine_ID
			INNER JOIN M_InOut io ON iol.M_InOut_ID = io.M_InOut_ID
			INNER JOIN M_HU_Assignment thuas ON iol.M_InOutLine_ID = thuas.Record_ID
			LEFT OUTER JOIN M_HU thu ON thuas.M_HU_ID = thu.M_HU_ID
			LEFT OUTER JOIN M_HU_Storage_Snapshot thuss ON thu.M_HU_ID = thuss.M_HU_ID
				AND thuss.Snapshot_UUID = io.Snapshot_UUID
			-- Get weight from snapshot
			LEFT OUTER JOIN M_HU_Attribute_Snapshot thuwns ON thu.M_HU_ID = thuwns.M_HU_ID
				AND thuwns.M_Attribute_ID = ((SELECT M_Attribute_ID FROM M_Attribute WHERE value = 'WeightNet'))
				AND thuwns.Snapshot_UUID = io.Snapshot_UUID
			-- Fallback: Get weight from regular HU structure
			LEFT OUTER JOIN M_HU_Attribute thuwn ON thu.M_HU_ID = thuwn.M_HU_ID
				AND thuwn.M_Attribute_ID = ((SELECT M_Attribute_ID FROM M_Attribute WHERE value = 'WeightNet'))
		WHERE
			ol.C_Order_ID = $1
			AND thuas.M_TU_HU_ID IS NULL AND thuas.AD_Table_ID = ((SELECT Get_Table_ID('M_InOutLine')))
			-- valuenumber will later be used in a coalesce. 0 is an invalid value
			AND COALESCE( thuwn.valuenumber, 0 ) != 0
	) thu ON iol.M_InOutLine_ID = thu.Record_ID
	LEFT OUTER JOIN C_UOM uom ON uom.C_UOM_ID = thu.C_UOM_ID
WHERE
	p.M_Product_Category_ID != (SELECT value::numeric FROM AD_SysConfig WHERE name = 'PackingMaterialProductCategoryID')
ORDER BY
	p.name, thu.value
$$
LANGUAGE sql STABLE;