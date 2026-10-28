def average(nums):
    total = 0
    for n in nums:
        total += n
    return total / len(nums)

def healthy_ratio(values, threshold):
    good = [v for v in values if v > threshold]
    return len(good) / len(values)

if __name__ == "__main__":
    print(average([]))
