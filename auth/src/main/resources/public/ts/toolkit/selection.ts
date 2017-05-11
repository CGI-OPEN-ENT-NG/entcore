export interface Selectable{
    selected: boolean;
}

export class Selection<T extends Selectable>{
    private selectedElements: T[];
    constructor(private arr: T[]){}

    selectAll(){
        for(let i = 0; i < this.arr.length; i++){
            this.arr[i].selected = true;
        }
    }

    select(filter: (T) => boolean){
        for(let i = 0; i < this.arr.length; i++){
            this.arr[i].selected = filter(this.arr[i]);
        }
    }

    deselect(filter: (T) => boolean){
        for(let i = 0; i < this.arr.length; i++){
            this.arr[i].selected = !filter(this.arr[i]);
        }
    }

    deselectAll() {
        for (let i = 0; i < this.arr.length; i++) {
            this.arr[i].selected = false;
        }
    }

    removeSelection() {
        let newArr = [];
        for (let i = 0; i < this.arr.length; i++) {
            if (this.arr[i].selected) {
                newArr.push(this.arr[i]);
            }
        }

        this.arr.splice(0, this.arr.length);
        for (let i = 0; i < newArr.length; i++) {
            this.arr.push(newArr[i]);
        }
    }

    updateSelected(){
        for(let i = 0; i < this.arr.length; i++){
            let index = this.selectedElements.indexOf(this.arr[i]);
            if(this.arr[i].selected && index === -1){
                this.selectedElements.push(this.arr[i]);
            }
            else if(!this.arr[i].selected && index !== -1){
                this.selectedElements.splice(index, 1);
            }
        }
    }

    // a specific array is maintained to avoid references breaking all the time
    get selected(): T[]{
        this.updateSelected();
        return this.selectedElements;
    }
}