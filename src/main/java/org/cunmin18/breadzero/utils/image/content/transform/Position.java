package org.cunmin18.breadzero.utils.image.content.transform;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Position{
    private int x;
    private int y;
    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }
    public Position(Position position){
        this.x = position.getX();
        this.y = position.getY();
    }
    public static Position ZERO(){
        return new Position(0,0);
    }
}
