package de.metas.document.model;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */


public interface IDocumentLocation
{

	int getC_BPartner_ID();

	org.compiere.model.I_C_BPartner getC_BPartner();

	int getC_BPartner_Location_ID();

	org.compiere.model.I_C_BPartner_Location getC_BPartner_Location();

	int getAD_User_ID();

	org.compiere.model.I_AD_User getAD_User();

	String getBPartnerAddress();
	void setBPartnerAddress(String address);

}
