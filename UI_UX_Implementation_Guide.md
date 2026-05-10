# UI/UX Implementation Guide
## Full-Screen Browser with Draggable FAB & Chrome-Level Interface

---

## 🎨 Design System & Color Scheme

### Material Design 3 Theme

```xml
<!-- res/values/themes.xml -->
<resources>
    <style name="Theme.GlassBox" parent="Theme.Material3.Dark">
        <!-- Primary Colors -->
        <item name="colorPrimary">@color/primary_color</item>
        <item name="colorOnPrimary">@color/on_primary</item>
        <item name="colorPrimaryContainer">@color/primary_container</item>
        <item name="colorOnPrimaryContainer">@color/on_primary_container</item>
        
        <!-- Secondary Colors -->
        <item name="colorSecondary">@color/secondary_color</item>
        <item name="colorOnSecondary">@color/on_secondary</item>
        
        <!-- Tertiary Colors -->
        <item name="colorTertiary">@color/tertiary_color</item>
        <item name="colorOnTertiary">@color/on_tertiary</item>
        
        <!-- Status Colors -->
        <item name="colorError">@color/error_color</item>
        <item name="colorOnError">@color/on_error</item>
        
        <!-- Surface Colors -->
        <item name="colorSurface">@color/surface</item>
        <item name="colorOnSurface">@color/on_surface</item>
        
        <!-- Typography -->
        <item name="fontFamily">@font/roboto</item>
        <item name="textAppearanceDisplayLarge">@style/TextAppearance.Material3.DisplayLarge</item>
        <item name="textAppearanceHeadlineSmall">@style/TextAppearance.Material3.HeadlineSmall</item>
    </style>
</resources>

<!-- res/values/colors.xml -->
<resources>
    <!-- Brand Colors -->
    <color name="primary_color">#1F2937</color>
    <color name="on_primary">#FFFFFF</color>
    <color name="primary_container">#3B82F6</color>
    <color name="on_primary_container">#FFFFFF</color>
    
    <color name="secondary_color">#6366F1</color>
    <color name="on_secondary">#FFFFFF</color>
    
    <color name="tertiary_color">#0EA5E9</color>
    <color name="on_tertiary">#FFFFFF</color>
    
    <!-- Status -->
    <color name="error_color">#F87171</color>
    <color name="on_error">#FFFFFF</color>
    <color name="success_color">#22C55E</color>
    <color name="warning_color">#FBBF24</color>
    
    <!-- Surface -->
    <color name="surface">#111827</color>
    <color name="on_surface">#F3F4F6</color>
    <color name="surface_variant">#1F2937</color>
    <color name="on_surface_variant">#9CA3AF</color>
    
    <!-- UI Elements -->
    <color name="toolbar_background">#111827</color>
    <color name="url_bar_background">#1F2937</color>
    <color name="fab_background">#3B82F6</color>
    <color name="fab_ripple">#60A5FA</color>
</resources>
```

---

## 📱 UI Layout Hierarchy

### Activity Layout Structure

```xml
<!-- res/layout/activity_browser.xml - Complete Full-Screen Layout -->
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/surface"
    tools:context=".activities.BrowserActivity">

    <!-- WebView - Main Content -->
    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/surface" />

    <!-- Toolbar with Controls -->
    <LinearLayout
        android:id="@+id/toolbar_container"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:orientation="horizontal"
        android:background="@color/toolbar_background"
        android:elevation="4dp"
        android:gravity="center_vertical">

        <!-- Back Button -->
        <ImageButton
            android:id="@+id/btn_back"
            style="@style/IconButton"
            android:contentDescription="@string/back"
            android:src="@drawable/ic_back" />

        <!-- Forward Button -->
        <ImageButton
            android:id="@+id/btn_forward"
            style="@style/IconButton"
            android:contentDescription="@string/forward"
            android:src="@drawable/ic_forward" />

        <!-- URL Bar with Material Design -->
        <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/url_input_layout"
            android:layout_width="0dp"
            android:layout_height="40dp"
            android:layout_weight="1"
            android:layout_marginHorizontal="8dp"
            android:layout_gravity="center_vertical"
            app:boxBackgroundColor="@color/url_bar_background"
            app:boxCornerRadiusBottomEnd="8dp"
            app:boxCornerRadiusBottomStart="8dp"
            app:boxCornerRadiusTopEnd="8dp"
            app:boxCornerRadiusTopStart="8dp"
            app:endIconMode="clear_text"
            app:hintAnimationEnabled="false">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/url_bar"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:hint="@string/search_or_type_url"
                android:inputType="textUri"
                android:imeOptions="actionGo"
                android:textColor="@color/on_surface"
                android:textColorHint="@color/on_surface_variant"
                android:textSize="14sp"
                android:paddingHorizontal="12dp" />

        </com.google.android.material.textfield.TextInputLayout>

        <!-- Refresh Button -->
        <ImageButton
            android:id="@+id/btn_refresh"
            style="@style/IconButton"
            android:contentDescription="@string/refresh"
            android:src="@drawable/ic_refresh" />

        <!-- More Menu Button -->
        <ImageButton
            android:id="@+id/btn_more"
            style="@style/IconButton"
            android:contentDescription="@string/more_options"
            android:src="@drawable/ic_more_vert" />

    </LinearLayout>

    <!-- Progress Bar (appears below toolbar) -->
    <ProgressBar
        android:id="@+id/progress_bar"
        android:layout_width="match_parent"
        android:layout_height="3dp"
        android:layout_marginTop="?attr/actionBarSize"
        android:indeterminate="true"
        android:progressDrawable="@drawable/progress_bar_gradient"
        android:visibility="gone" />

    <!-- Page Loading Indicator -->
    <FrameLayout
        android:id="@+id/loading_overlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/surface"
        android:visibility="gone">

        <ProgressBar
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_gravity="center"
            android:indeterminate="true" />

        <TextView
            android:id="@+id/loading_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center_horizontal|bottom"
            android:layout_marginBottom="32dp"
            android:text="Loading..."
            android:textColor="@color/on_surface" />

    </FrameLayout>

    <!-- Floating Return Button (Draggable) -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_return"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:layout_gravity="bottom|start"
        android:layout_margin="16dp"
        android:contentDescription="@string/return_to_app"
        android:src="@drawable/ic_hello_icon"
        android:elevation="6dp"
        app:backgroundTint="@color/fab_background"
        app:rippleColor="@color/fab_ripple"
        app:borderWidth="0dp"
        app:fabSize="normal"
        app:maxImageSize="28dp" />

    <!-- Floating Profile Menu (Optional) -->
    <FrameLayout
        android:id="@+id/fab_profile_menu"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:visibility="gone">

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/fab_profile"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:contentDescription="@string/profile"
            android:src="@drawable/ic_profile"
            app:backgroundTint="@color/secondary_color"
            app:elevation="6dp" />

    </FrameLayout>

    <!-- Speed Dial Menu (Optional - for more actions) -->
    <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
        android:id="@+id/fab_expanded"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="20dp"
        android:contentDescription="@string/more_options"
        android:text="@string/menu"
        android:visibility="gone"
        app:icon="@drawable/ic_menu"
        app:backgroundTint="@color/primary_container" />

    <!-- Bottom Sheet Menu -->
    <include
        android:id="@+id/bottom_sheet_menu"
        layout="@layout/bottom_sheet_browser_menu"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom" />

</FrameLayout>
```

### URL Bar Component with Material Design

```xml
<!-- res/layout/component_url_bar.xml -->
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:layout_margin="8dp"
    app:cardBackgroundColor="@color/url_bar_background"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="12dp">

        <!-- Icon -->
        <ImageView
            android:id="@+id/url_icon"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:contentDescription="@null"
            android:src="@drawable/ic_globe"
            android:tint="@color/on_surface_variant"
            android:layout_marginEnd="8dp" />

        <!-- Input -->
        <EditText
            android:id="@+id/url_input"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@android:color/transparent"
            android:hint="@string/search_or_type_url"
            android:inputType="textUri"
            android:imeOptions="actionGo"
            android:textColor="@color/on_surface"
            android:textColorHint="@color/on_surface_variant"
            android:textSize="16sp"
            android:paddingVertical="0dp" />

        <!-- Clear Button -->
        <ImageButton
            android:id="@+id/clear_button"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/clear"
            android:src="@drawable/ic_close"
            android:tint="@color/on_surface_variant"
            android:scaleType="centerInside" />

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

### Bottom Sheet Menu

```xml
<!-- res/layout/bottom_sheet_browser_menu.xml -->
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.bottomsheet.BottomSheetDialog xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp"
        app:layout_behavior="@string/bottom_sheet_behavior">

        <!-- Drag Handle -->
        <View
            android:layout_width="40dp"
            android:layout_height="4dp"
            android:background="@drawable/shape_drag_handle"
            android:layout_gravity="center_horizontal"
            android:layout_marginBottom="16dp" />

        <!-- Menu Items in Grid -->
        <GridLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:columnCount="4"
            android:rowCount="2"
            android:layout_marginBottom="16dp">

            <!-- Share -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="8dp">

                <ImageView
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:src="@drawable/ic_share"
                    android:contentDescription="@string/share"
                    android:tint="@color/primary_container"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/share"
                    android:textSize="12sp"
                    android:textColor="@color/on_surface" />

            </LinearLayout>

            <!-- Bookmark -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="8dp">

                <ImageView
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:src="@drawable/ic_bookmark"
                    android:contentDescription="@string/bookmark"
                    android:tint="@color/secondary_color"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/bookmark"
                    android:textSize="12sp"
                    android:textColor="@color/on_surface" />

            </LinearLayout>

            <!-- Download -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="8dp">

                <ImageView
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:src="@drawable/ic_download"
                    android:contentDescription="@string/downloads"
                    android:tint="@color/tertiary_color"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/downloads"
                    android:textSize="12sp"
                    android:textColor="@color/on_surface" />

            </LinearLayout>

            <!-- History -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="8dp">

                <ImageView
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:src="@drawable/ic_history"
                    android:contentDescription="@string/history"
                    android:tint="@color/error_color"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/history"
                    android:textSize="12sp"
                    android:textColor="@color/on_surface" />

            </LinearLayout>

            <!-- Print -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="8dp">

                <ImageView
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:src="@drawable/ic_print"
                    android:contentDescription="@string/print"
                    android:tint="@color/on_surface_variant"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/print"
                    android:textSize="12sp"
                    android:textColor="@color/on_surface" />

            </LinearLayout>

            <!-- Find -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="8dp">

                <ImageView
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:src="@drawable/ic_find"
                    android:contentDescription="@string/find"
                    android:tint="@color/warning_color"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/find"
                    android:textSize="12sp"
                    android:textColor="@color/on_surface" />

            </LinearLayout>

            <!-- Settings -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_columnWeight="1"
                android:orientation="vertical"
                android:gravity="center"
                android:padding="8dp">

                <ImageView
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:src="@drawable/ic_settings"
                    android:contentDescription="@string/settings"
                    android:tint="@color/surface_variant"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/settings"
                    android:textSize="12sp"
                    android:textColor="@color/on_surface" />

            </LinearLayout>

        </GridLayout>

        <!-- Divider -->
        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="@color/surface_variant"
            android:layout_marginBottom="16dp" />

        <!-- Profile Section -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:padding="12dp">

            <!-- Profile Avatar -->
            <com.google.android.material.imageview.ShapeableImageView
                android:id="@+id/profile_avatar"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:src="@drawable/ic_placeholder_avatar"
                android:contentDescription="@string/profile"
                android:scaleType="centerCrop"
                android:layout_marginEnd="12dp"
                app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.CornerExtraLarge" />

            <!-- Profile Info -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/profile_name"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/default_profile"
                    android:textSize="16sp"
                    android:textColor="@color/on_surface"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/profile_email"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/default_email"
                    android:textSize="12sp"
                    android:textColor="@color/on_surface_variant" />

            </LinearLayout>

            <!-- Switch Profile Button -->
            <ImageButton
                android:id="@+id/btn_switch_profile"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/switch_profile"
                android:src="@drawable/ic_arrow_right"
                android:tint="@color/primary_container"
                android:scaleType="centerInside" />

        </LinearLayout>

    </LinearLayout>

</com.google.android.material.bottomsheet.BottomSheetDialog>
```

---

## 🎯 Draggable Floating Action Button Implementation

### CustomDraggableView.kt

```kotlin
class DraggableFloatingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private var lastX = 0f
    private var lastY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val dragThreshold = dpToPx(8f)
    private val snapAnimationDuration = 300L
    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels
    
    private var onSnapListener: ((Float, Float) -> Unit)? = null
    
    init {
        setOnTouchListener { _, event ->
            handleDrag(event)
        }
    }
    
    private fun handleDrag(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                lastX = x
                lastY = y
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                
                // Only start dragging if movement exceeds threshold
                if (!isDragging && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) {
                    isDragging = true
                }
                
                if (isDragging) {
                    val newX = lastX + dx
                    val newY = lastY + dy
                    
                    // Boundary checking
                    val constrainedX = newX.coerceIn(0f, (screenWidth - width).toFloat())
                    val constrainedY = newY.coerceIn(0f, (screenHeight - height).toFloat())
                    
                    x = constrainedX
                    y = constrainedY
                    
                    // Visual feedback - slight scale change
                    scaleX = 1.05f
                    scaleY = 1.05f
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    // Snap to nearest edge
                    snapToEdge()
                    isDragging = false
                    
                    // Reset scale
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                return true
            }
        }
        return false
    }
    
    private fun snapToEdge() {
        val currentX = x
        val targetX = if (currentX < screenWidth / 2) {
            0f
        } else {
            (screenWidth - width).toFloat()
        }
        
        animate()
            .x(targetX)
            .setDuration(snapAnimationDuration)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                onSnapListener?.invoke(targetX, y)
            }
            .start()
    }
    
    fun setOnSnapListener(listener: (Float, Float) -> Unit) {
        onSnapListener = listener
    }
    
    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
```

### Activity Implementation with FAB

```kotlin
class BrowserActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBrowserBinding
    private lateinit var draggableFab: DraggableFloatingButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupFab()
    }
    
    private fun setupFab() {
        draggableFab = binding.fabReturn
        
        draggableFab.setOnSnapListener { finalX, finalY ->
            // Save FAB position to preferences
            saveFabPosition(finalX, finalY)
        }
        
        draggableFab.setOnClickListener {
            animateReturnToApp()
        }
        
        // Long press for menu
        draggableFab.setOnLongClickListener {
            showFabContextMenu()
            true
        }
        
        // Restore previous position
        restoreFabPosition()
    }
    
    private fun animateReturnToApp() {
        // Animate FAB out
        draggableFab.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            .start()
    }
    
    private fun saveFabPosition(x: Float, y: Float) {
        getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("fab_x", x)
            .putFloat("fab_y", y)
            .apply()
    }
    
    private fun restoreFabPosition() {
        val prefs = getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val savedX = prefs.getFloat("fab_x", -1f)
        val savedY = prefs.getFloat("fab_y", -1f)
        
        if (savedX >= 0 && savedY >= 0) {
            draggableFab.x = savedX
            draggableFab.y = savedY
        }
    }
    
    private fun showFabContextMenu() {
        val menu = PopupMenu(this, draggableFab)
        menu.menuInflater.inflate(R.menu.fab_context_menu, menu.menu)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_tab -> newTab()
                R.id.action_clear_cache -> clearCache()
                R.id.action_settings -> openSettings()
                else -> false
            }
        }
        menu.show()
    }
}
```

---

## 📊 Profile Switcher UI

### Dialog Implementation

```kotlin
class ProfileSwitchDialog(
    private val profiles: List<ProfileEntity>,
    private val onProfileSelected: (ProfileEntity) -> Unit,
    private val onAddProfile: () -> Unit
) : DialogFragment() {
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Switch Profile")
            .setAdapter(ProfileAdapter(profiles)) { dialog, which ->
                onProfileSelected(profiles[which])
                dialog.dismiss()
            }
            .setNegativeButton("Add Profile") { _, _ ->
                onAddProfile()
            }
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
    
    private inner class ProfileAdapter(
        private val items: List<ProfileEntity>
    ) : ArrayAdapter<ProfileEntity>(
        requireContext(),
        R.layout.item_profile,
        items
    ) {
        
        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_profile, parent, false)
            
            val profile = items[position]
            
            view.findViewById<TextView>(R.id.profile_name).text = profile.name
            view.findViewById<TextView>(R.id.profile_email).text = profile.email ?: "No email"
            view.findViewById<ImageView>(R.id.profile_icon).apply {
                setImageResource(getProfileIcon(profile.type))
                imageTintList = ColorStateList.valueOf(
                    if (profile.isActive) Color.GREEN else Color.GRAY
                )
            }
            
            return view
        }
        
        private fun getProfileIcon(type: String): Int = when (type) {
            "gmail" -> R.drawable.ic_gmail
            "outlook" -> R.drawable.ic_outlook
            "icloud" -> R.drawable.ic_icloud
            else -> R.drawable.ic_profile
        }
    }
}
```

### Layout for Profile Item

```xml
<!-- res/layout/item_profile.xml -->
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="72dp"
    android:layout_margin="8dp"
    app:cardBackgroundColor="@color/surface_variant"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="12dp">

        <!-- Profile Icon -->
        <com.google.android.material.imageview.ShapeableImageView
            android:id="@+id/profile_icon"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@drawable/ic_profile"
            android:contentDescription="@string/profile"
            android:scaleType="centerCrop"
            android:layout_marginEnd="12dp"
            app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.Corner.ExtraLarge" />

        <!-- Profile Info -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/profile_name"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Profile Name"
                android:textSize="16sp"
                android:textColor="@color/on_surface"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/profile_email"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="email@example.com"
                android:textSize="12sp"
                android:textColor="@color/on_surface_variant" />

        </LinearLayout>

        <!-- Active Indicator -->
        <ImageView
            android:id="@+id/active_indicator"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_check_circle"
            android:contentDescription="@string/active"
            android:tint="@color/success_color" />

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

---

## 📲 Animations & Transitions

### Smooth Page Transitions

```kotlin
// Transition between pages
private fun transitionToNewPage(url: String) {
    val currentView = binding.webView
    
    // Fade out current content
    currentView.animate()
        .alpha(0.5f)
        .setDuration(150)
        .withEndAction {
            // Load new URL
            currentView.loadUrl(url)
            
            // Fade in new content
            currentView.animate()
                .alpha(1f)
                .setDuration(150)
                .start()
        }
        .start()
}

// Toolbar slide animation
private fun slideToolbarUp() {
    binding.toolbarContainer.animate()
        .translationY(-binding.toolbarContainer.height.toFloat())
        .setDuration(300)
        .setInterpolator(FastOutLinearInInterpolator())
        .start()
}

private fun slideToolbarDown() {
    binding.toolbarContainer.animate()
        .translationY(0f)
        .setDuration(300)
        .setInterpolator(LinearOutSlowInInterpolator())
        .start()
}
```

### Shared Element Transitions

```xml
<!-- res/transition/shared_element_transition.xml -->
<?xml version="1.0" encoding="utf-8"?>
<transitionSet xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="300"
    android:interpolator="@interpolator/fast_out_slow_in">
    
    <changeImageTransform />
    <changeBounds />
    <changeScaleAndRotation />
    
</transitionSet>
```

---

## ♿ Accessibility Features

### Content Descriptions & Labels

```xml
<!-- res/values/strings.xml -->
<resources>
    <!-- Browser Controls -->
    <string name="back">Go back</string>
    <string name="forward">Go forward</string>
    <string name="refresh">Refresh page</string>
    <string name="return_to_app">Return to Hello app</string>
    
    <!-- Status Messages -->
    <string name="page_loading">Page is loading</string>
    <string name="page_loaded">Page loaded successfully</string>
    <string name="page_error">Error loading page</string>
    
    <!-- Profile -->
    <string name="switch_profile">Switch profile</string>
    <string name="active">Active profile</string>
</resources>
```

### Screen Reader Support

```kotlin
// Announce page changes
private fun announcePageChange(title: String) {
    binding.webView.announceForAccessibility(
        "Page loaded: $title"
    )
}

// Enable accessibility features
private fun setupAccessibility() {
    binding.webView.accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(
            host: View?,
            info: AccessibilityNodeInfo?
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info?.text = "Browser content"
        }
    }
}
```

---

## 🎬 Complete User Flow Diagram

```
┌─────────────────────────────────────────────────────────┐
│             User Opens Browser Activity                 │
└──────────────────────┬──────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
    Load URL          Load Homepage
        │                             │
        └──────────────┬──────────────┘
                       │
         ┌─────────────v─────────────┐
         │   Toolbar Appears         │
         │   (with animation)        │
         └─────────────┬─────────────┘
                       │
         ┌─────────────v─────────────┐
         │   WebView Loads Content   │
         │   Progress Bar Shows      │
         └─────────────┬─────────────┘
                       │
         ┌─────────────v─────────────┐
         │   FAB Ready to Interact   │
         │   (draggable)             │
         └─────────────┬─────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
    User Drags FAB             User Clicks FAB
        │                             │
        │                             v
    Snap to Edge          Animate Return
        │                    (scale out)
        │                             │
        └──────────────┬──────────────┘
                       │
         ┌─────────────v─────────────┐
         │   Return to Main App      │
         │   (with transition)       │
         └─────────────────────────────┘
```

This complete UI/UX implementation ensures a **Chrome-level browser experience** with intuitive controls and smooth interactions! 🎨✨
