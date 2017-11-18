/*
 * CSCE 4444
 * 
 * 
 */
package Food;


import java.util.ArrayList;

//THIS IS ONLY FOR SERVER TESTING 
public class MerchandiseList
{
    
    /**
     *
     */
    public ArrayList<Food> merchandise;
    Merch merch1;
    Merch merch2;
    Merch merch3;
    
    
    public MerchandiseList()
    {        
        merchandise = new ArrayList<>();
        
        merch1 = new Merch("Shirt", "Merchandise", "merch",15.99, Boolean.TRUE, Boolean.FALSE,"Medium");
        //merch2.SetDescription("Some pretty good shirts\n");
        
        
        merch2 = new Merch("Mug", "Merchandise", "merch",16.99, Boolean.TRUE, Boolean.FALSE, "Small");
        //merch2.SetDescription("I mean, cmon.\n");
        
        
        merch3 = new Merch("Hat", "Merchandise", "merch", 17.99, Boolean.TRUE, Boolean.FALSE, "Large");
        //merch3.SetDescription("I have no clue\n");
        
        
        merchandise.add(merch1);
        merchandise.add(merch2);
        merchandise.add(merch3);
    }    
    
}
