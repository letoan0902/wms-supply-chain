package vn.wms.inventory.dto;

import vn.wms.inventory.entity.StocktakeLocation;

/**
 * Vị trí thuộc phạm vi đợt kiểm kê.
 *
 * @param binLocationId   ID vị trí
 * @param binLocationCode mã vị trí
 * @param zone            khu
 * @param rack            dãy kệ
 * @param bin             ô
 * @param frozen          vị trí đang bị đóng băng bởi đợt này
 */
public record StocktakeLocationResponse(Long binLocationId, String binLocationCode, String zone, String rack,
                                        String bin, boolean frozen) {

    public static StocktakeLocationResponse from(StocktakeLocation location) {
        return new StocktakeLocationResponse(
                location.getBinLocation().getId(),
                location.getBinLocation().getCode(),
                location.getBinLocation().getZone(),
                location.getBinLocation().getRack(),
                location.getBinLocation().getBin(),
                location.isFrozen());
    }
}
