/*
 * CSCE 4444
 * 
 * 
 */
package Food;


import java.util.ArrayList;

//THIS IS ONLY FOR SERVER TESTING
public class DessertsList
{
    public ArrayList<Food> desserts;
    Food dessert1;
    Food dessert2;
    Food dessert3;
    Food dessert4;
    
    
    public DessertsList()
    {
        desserts = new ArrayList<>();
        
        dessert1 = new Food("Cake", "Food", "dessert", 7.99, Boolean.TRUE, Boolean.FALSE);
        dessert2 = new Food("Ice Cream", "Food", "dessert", 8.99, Boolean.TRUE, Boolean.TRUE);
        dessert3 = new Food("Cake and Ice Cream", "Food", "dessert", 9.99, Boolean.TRUE, Boolean.FALSE);
        dessert4 = new Food("Holiday Cookie", "Food", "dessert", 3.00, Boolean.TRUE, Boolean.TRUE);
        
        desserts.add(dessert1);
        desserts.add(dessert2);
        desserts.add(dessert3);
        desserts.add(dessert4);
    }
    
}
