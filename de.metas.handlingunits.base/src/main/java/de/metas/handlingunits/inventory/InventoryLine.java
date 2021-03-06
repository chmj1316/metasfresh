package de.metas.handlingunits.inventory;

import javax.annotation.Nullable;

import org.adempiere.mm.attributes.AttributeSetInstanceId;
import org.adempiere.warehouse.LocatorId;

import com.google.common.collect.ImmutableList;

import de.metas.inventory.InventoryId;
import de.metas.inventory.InventoryLineId;
import de.metas.material.event.commons.AttributesKey;
import de.metas.product.ProductId;
import de.metas.quantity.Quantity;
import de.metas.util.collections.CollectionUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2019 metas GmbH
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

@Value
@Builder(toBuilder = true)
public class InventoryLine
{
	/** If not null then {@link InventoryLineRepository#save(InventoryLine)} will load and sync the respective {@code M_InventoryLine} record */
	@Nullable
	InventoryLineId id;

	/** If not null then {@link InventoryLineRepository#save(InventoryLine)} will assume that there is an existing persisted ASI which is in sync with {@link #storageAttributesKey}. */
	@Nullable
	AttributeSetInstanceId asiId;

	@NonNull
	InventoryId inventoryId;

	@NonNull
	ProductId productId;

	@NonNull
	AttributesKey storageAttributesKey;

	@NonNull
	LocatorId locatorId;

	boolean singleHUAggregation;

	@Singular("inventoryLineHU")
	ImmutableList<InventoryLineHU> inventoryLineHUs;

	public InventoryLineHU getSingleHU()
	{
		return CollectionUtils.singleElement(inventoryLineHUs);
	}

	public InventoryLine withQtyCount(@NonNull final Quantity qtyCount)
	{
		final ImmutableList<InventoryLineHU> inventoryLineHUsToIerate;
		if (inventoryLineHUs.isEmpty())
		{
			inventoryLineHUsToIerate = ImmutableList.of(InventoryLineHU
					.builder()
					.qtyBook(qtyCount.toZero())
					.qtyCount(qtyCount.toZero())
					.build());
		}
		else
		{
			inventoryLineHUsToIerate = inventoryLineHUs;
		}

		final Quantity currentQtyCount = inventoryLineHUsToIerate
				.stream()
				.map(InventoryLineHU::getQtyCount)
				.reduce(qtyCount.toZero(), Quantity::add);

		Quantity qtyDiffLeftToDistribute = qtyCount.subtract(currentQtyCount);

		final InventoryLineBuilder builder = this.toBuilder().clearInventoryLineHUs();

		for (final InventoryLineHU inventoryLineHU : inventoryLineHUsToIerate)
		{
			if (qtyDiffLeftToDistribute.signum() > 0)
			{
				builder.inventoryLineHU(
						inventoryLineHU.addCountQty(qtyDiffLeftToDistribute));
				qtyDiffLeftToDistribute = qtyDiffLeftToDistribute.toZero();
			}
			else if (qtyDiffLeftToDistribute.signum() < 0)
			{
				final boolean qtyToSubtractIsGreaterThanLineQty = qtyDiffLeftToDistribute.negate()
						.compareTo(inventoryLineHU.getQtyCount()) > 0;

				if (qtyToSubtractIsGreaterThanLineQty)
				{
					qtyDiffLeftToDistribute = qtyDiffLeftToDistribute.add(
							inventoryLineHU.getQtyCount());

					builder.inventoryLineHU(inventoryLineHU.zeroQtyCount());
				}
				else
				{
					builder.inventoryLineHU(
							inventoryLineHU.addCountQty(qtyDiffLeftToDistribute));
					qtyDiffLeftToDistribute = qtyDiffLeftToDistribute.toZero();
				}
			}
			else
			{
				builder.inventoryLineHU(inventoryLineHU); // just add it unchanged
			}
		}
		return builder.build();
	}
}
