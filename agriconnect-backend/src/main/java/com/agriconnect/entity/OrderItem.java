package com.agriconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One crop line inside an Order.
 *
 * <p>Every displayable field is snapshotted (crop name, unit, price, seller name) so a placed
 * order stays accurate even if the crop is later edited or deleted. The crop / farmer /
 * collectedFarmer relationships are kept for querying and are all ON DELETE SET NULL for the
 * same reason: existing crop-deletion endpoints must keep working.
 *
 * <p>Seller attribution mirrors Crop exactly - a crop is owned either by a registered User
 * farmer or by a CollectedFarmer added by a Field Coordinator, never both. sellerName is the
 * resolved display name so callers never have to guess.
 */
@Entity
@Table(name = "order_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    public static final String SELLER_TYPE_FARMER = "FARMER";
    public static final String SELLER_TYPE_COLLECTED_FARMER = "COLLECTED_FARMER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crop_name", nullable = false, length = 100)
    private String cropName;

    @Column(length = 50)
    private String category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    /** Resolved seller display name - farmer full name or collected farmer name. */
    @Column(name = "seller_name", nullable = false, length = 150)
    private String sellerName;

    @Column(name = "seller_type", length = 20)
    private String sellerType;

    @Column(name = "location", length = 100)
    private String location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Crop crop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farmer_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User farmer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_farmer_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CollectedFarmer collectedFarmer;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCropName() {
		return cropName;
	}

	public void setCropName(String cropName) {
		this.cropName = cropName;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public void setTotalPrice(BigDecimal totalPrice) {
		this.totalPrice = totalPrice;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public String getSellerName() {
		return sellerName;
	}

	public void setSellerName(String sellerName) {
		this.sellerName = sellerName;
	}

	public String getSellerType() {
		return sellerType;
	}

	public void setSellerType(String sellerType) {
		this.sellerType = sellerType;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public Order getOrder() {
		return order;
	}

	public void setOrder(Order order) {
		this.order = order;
	}

	public Crop getCrop() {
		return crop;
	}

	public void setCrop(Crop crop) {
		this.crop = crop;
	}

	public User getFarmer() {
		return farmer;
	}

	public void setFarmer(User farmer) {
		this.farmer = farmer;
	}

	public CollectedFarmer getCollectedFarmer() {
		return collectedFarmer;
	}

	public void setCollectedFarmer(CollectedFarmer collectedFarmer) {
		this.collectedFarmer = collectedFarmer;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public static String getSellerTypeFarmer() {
		return SELLER_TYPE_FARMER;
	}

	public static String getSellerTypeCollectedFarmer() {
		return SELLER_TYPE_COLLECTED_FARMER;
	}
    
    
}
