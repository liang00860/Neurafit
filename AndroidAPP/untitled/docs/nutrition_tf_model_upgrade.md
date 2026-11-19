# Nutrition TF Model Upgrade

This app currently expects a TensorFlow Lite food detector with SSD-style outputs:

- `food_detect.tflite`
- `food_labelmap.txt`
- `calorie_info.txt`
- output 0: boxes `[1, N, 4]` in `[top, left, bottom, right]`
- output 1: classes `[1, N]`
- output 2: scores `[1, N]`
- output 3: detection count `[1]`

## Current Limit

The bundled Food.AI label map has only 16 simple classes, so app-side inference can improve multi-food recall, but the model itself still cannot reliably recognize real complex dishes such as rice bowls, stir-fried dishes, noodles with toppings, dumplings, salad components, or mixed lunch plates unless those labels exist in the trained model.

## Target Dataset

Use a COCO-style detection or instance-segmentation dataset:

- `images/train`, `images/val`, `images/test`
- `annotations/train.json`, `annotations/val.json`, `annotations/test.json`
- one category per dish or ingredient class
- enough real phone photos per class across lighting, bowls/plates, camera angles, occlusion, and partial portions

Recommended first label expansion:

- staple: rice, noodle, bread
- protein: chicken breast, egg, beef, fish, tofu
- mixed dishes: salad, dumpling, fried rice, rice bowl, noodle soup, stir fry
- sides: french fries, fruit, vegetables

## Export Contract

When replacing the model:

1. Export to TFLite with the same output contract above, or add a new Kotlin adapter for the new output format.
2. Update `app/src/main/assets/food_labelmap.txt` to exactly match model class order.
3. Update `app/src/main/assets/calorie_info.txt` with one kcal value per class.
4. Add nutrition profiles in `FoodNutritionEstimator` for every new label.
5. Validate on-device with at least 50 real meal photos before shipping.

## Acceptance Gates

- detects at least 2 food items in mixed plates where multiple labeled foods are visible
- does not double-count the same food from overlapping crops
- common complex meals return a useful fallback only when confidence is low
- debug APK latency stays acceptable for still-photo analysis
- nutrition estimate changes with visible food area instead of using one fixed serving for every photo
